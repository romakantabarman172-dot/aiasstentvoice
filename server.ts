import 'dotenv/config';
import express, { type Request, type Response } from 'express';
import {
  type Content,
  type FunctionDeclaration,
  GoogleGenAI,
  Type,
} from '@google/genai';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = 3000;
const MODEL_NAME = 'gemini-4-hydrogen';

function createAiClient(): GoogleGenAI {
  const apiKey = process.env.GEMINI_API_KEY?.trim();
  if (!apiKey || apiKey === 'MY_GEMINI_API_KEY') {
    throw new Error(
      'GEMINI_API_KEY is not configured. Please add your Gemini API key in Settings > Secrets.'
    );
  }
  return new GoogleGenAI({
    apiKey,
    httpOptions: {
      headers: {
        'User-Agent': 'aistudio-build',
      },
    },
  });
}

const assistantTools: FunctionDeclaration[] = [
  {
    name: 'add_task',
    description: 'Add a new daily task or to-do item for the user.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        title: { type: Type.STRING, description: 'Short actionable task title' },
        category: {
          type: Type.STRING,
          description: 'Category such as Work, Personal, Health, Shopping, Errands',
        },
        priority: {
          type: Type.STRING,
          description: 'Priority level: High, Normal, or Low',
        },
        dueLabel: {
          type: Type.STRING,
          description: 'Human-readable due window like Today, Evening, Tomorrow',
        },
      },
      required: ['title'],
    },
  },
  {
    name: 'complete_task',
    description: 'Mark an existing task as completed.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        taskTitle: {
          type: Type.STRING,
          description: 'Title or keyword of the task to mark complete',
        },
      },
      required: ['taskTitle'],
    },
  },
  {
    name: 'delete_task',
    description: 'Delete a task from the user task list.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        taskTitle: {
          type: Type.STRING,
          description: 'Title or keyword of the task to remove',
        },
      },
      required: ['taskTitle'],
    },
  },
  {
    name: 'set_reminder',
    description: 'Schedule a timed reminder notification for the user.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        title: { type: Type.STRING, description: 'What to remind the user about' },
        delayMinutes: {
          type: Type.INTEGER,
          description: 'Minutes from now when the reminder should trigger (minimum 1)',
        },
        timeLabel: {
          type: Type.STRING,
          description: 'Readable schedule label, e.g., Today, 6:30 PM or In 10 mins',
        },
      },
      required: ['title', 'delayMinutes', 'timeLabel'],
    },
  },
  {
    name: 'make_call',
    description: 'Place a phone call to a contact name or phone number.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        contactNameOrNumber: {
          type: Type.STRING,
          description: 'Contact name (like Mom, Rahul) or phone number',
        },
      },
      required: ['contactNameOrNumber'],
    },
  },
  {
    name: 'manage_call',
    description: 'Answer, decline, end, mute, or switch speaker on an incoming or active call.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        action: {
          type: Type.STRING,
          description: 'One of: answer, decline, end, speaker, mute',
        },
      },
      required: ['action'],
    },
  },
  {
    name: 'send_message',
    description: 'Send an SMS or WhatsApp message to a contact or phone number.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        recipient: {
          type: Type.STRING,
          description: 'Recipient contact name or phone number',
        },
        message: { type: Type.STRING, description: 'Message text to send' },
        channel: {
          type: Type.STRING,
          description: 'Messaging channel: sms or whatsapp',
        },
      },
      required: ['recipient', 'message'],
    },
  },
  {
    name: 'open_app',
    description:
      'Open an app on the phone (e.g., WhatsApp, YouTube, Camera, Settings, Maps, Chrome, Instagram, Spotify, Gmail, Clock).',
    parameters: {
      type: Type.OBJECT,
      properties: {
        appName: { type: Type.STRING, description: 'Name of the app to open' },
      },
      required: ['appName'],
    },
  },
  {
    name: 'control_device',
    description:
      'Control device quick settings like flashlight, wifi, bluetooth, dnd, or volume.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        setting: {
          type: Type.STRING,
          description: 'Setting name: flashlight, wifi, bluetooth, dnd, volume',
        },
        enabled: {
          type: Type.BOOLEAN,
          description: 'True to turn on, false to turn off',
        },
        volumeLevel: {
          type: Type.INTEGER,
          description: 'Optional volume percentage 0 to 100 when setting is volume',
        },
      },
      required: ['setting'],
    },
  },
];

interface ClientStateContext {
  tasks: Array<{ title: string; category: string; priority: string; dueLabel: string; isCompleted: boolean }>;
  reminders: Array<{ title: string; timeLabel: string; isFired: boolean; isDismissed: boolean }>;
  contacts: Array<{ name: string; phone: string; relation?: string }>;
  deviceState: {
    flashlight: boolean;
    wifi: boolean;
    bluetooth: boolean;
    dnd: boolean;
    volume: number;
    activeApp: string | null;
    call: { status: string; contactName: string; phoneNumber: string };
  };
}

function simulateToolPreviewResult(
  name: string,
  args: Record<string, unknown>,
  ctx: ClientStateContext
): Record<string, unknown> {
  switch (name) {
    case 'add_task':
      return { status: 'added', title: String(args.title ?? 'Task') };
    case 'complete_task': {
      const q = String(args.taskTitle ?? '').toLowerCase();
      const found = ctx.tasks.find((t) => t.title.toLowerCase().includes(q));
      return found
        ? { status: 'completed', title: found.title }
        : { status: 'not_found', query: q };
    }
    case 'delete_task': {
      const q = String(args.taskTitle ?? '').toLowerCase();
      const found = ctx.tasks.find((t) => t.title.toLowerCase().includes(q));
      return found
        ? { status: 'deleted', title: found.title }
        : { status: 'not_found', query: q };
    }
    case 'set_reminder':
      return {
        status: 'scheduled',
        title: String(args.title ?? 'Reminder'),
        delayMinutes: Number(args.delayMinutes ?? 10),
        timeLabel: String(args.timeLabel ?? 'Soon'),
      };
    case 'make_call': {
      const target = String(args.contactNameOrNumber ?? '').trim();
      const contact = ctx.contacts.find(
        (c) =>
          c.name.toLowerCase().includes(target.toLowerCase()) ||
          (c.relation && c.relation.toLowerCase().includes(target.toLowerCase()))
      );
      return {
        status: 'calling',
        recipient: contact?.name ?? target,
        phone: contact?.phone ?? target,
      };
    }
    case 'manage_call': {
      const action = String(args.action ?? 'answer').toLowerCase();
      if (action === 'answer' && ctx.deviceState.call.status !== 'incoming') {
        return {
          status: 'no_incoming_call',
          detail: 'There is no incoming call ringing right now.',
        };
      }
      if ((action === 'decline' || action === 'end') && ctx.deviceState.call.status === 'idle') {
        return {
          status: 'no_active_call',
          detail: 'There is no active or ringing call to end.',
        };
      }
      return {
        status: 'ok',
        action,
        contact: ctx.deviceState.call.contactName || 'Caller',
      };
    }
    case 'send_message':
      return {
        status: 'ready',
        recipient: String(args.recipient ?? ''),
        channel: String(args.channel ?? 'sms'),
        message: String(args.message ?? ''),
      };
    case 'open_app':
      return {
        status: 'opened',
        appName: String(args.appName ?? ''),
      };
    case 'control_device':
      return {
        status: 'updated',
        setting: String(args.setting ?? ''),
        enabled: Boolean(args.enabled ?? true),
      };
    default:
      return { status: 'unknown_tool' };
  }
}

async function startServer() {
  const app = express();
  app.use(express.json({ limit: '1mb' }));

  app.get('/api/health', (_req: Request, res: Response) => {
    res.json({ ok: true, model: MODEL_NAME });
  });

  app.post('/api/assistant', async (req: Request, res: Response) => {
    try {
      const ai = createAiClient();
      const {
        message,
        history = [],
        context,
      } = req.body as {
        message?: string;
        history?: Array<{ isUser: boolean; text: string }>;
        context?: ClientStateContext;
      };

      const userMessage = typeof message === 'string' ? message.trim() : '';
      if (!userMessage) {
        res.status(400).json({ error: 'Message cannot be empty.' });
        return;
      }

      const safeContext: ClientStateContext = context ?? {
        tasks: [],
        reminders: [],
        contacts: [],
        deviceState: {
          flashlight: false,
          wifi: true,
          bluetooth: true,
          dnd: false,
          volume: 75,
          activeApp: null,
          call: { status: 'idle', contactName: '', phoneNumber: '' },
        },
      };

      const nowStr = new Date().toLocaleString('en-IN', {
        weekday: 'long',
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });

      const tasksSummary =
        safeContext.tasks.length === 0
          ? 'No tasks yet.'
          : safeContext.tasks
              .slice(0, 15)
              .map(
                (t) =>
                  `- [${t.isCompleted ? 'Done' : 'Pending'}] ${t.title} (${t.category}, ${t.priority}, ${t.dueLabel})`
              )
              .join('\n');

      const remindersSummary =
        safeContext.reminders.filter((r) => !r.isDismissed).length === 0
          ? 'No active reminders.'
          : safeContext.reminders
              .filter((r) => !r.isDismissed)
              .slice(0, 10)
              .map((r) => `- ${r.title} (${r.timeLabel}${r.isFired ? ', Triggered' : ''})`)
              .join('\n');

      const contactsSummary =
        safeContext.contacts.length === 0
          ? 'No saved contacts.'
          : safeContext.contacts
              .map((c) => `- ${c.name}${c.relation ? ` (${c.relation})` : ''}: ${c.phone}`)
              .join('\n');

      const callInfo =
        safeContext.deviceState.call.status === 'idle'
          ? 'No active or incoming call'
          : `${safeContext.deviceState.call.status.toUpperCase()} call with ${safeContext.deviceState.call.contactName} (${safeContext.deviceState.call.phoneNumber})`;

      const systemInstruction = `
You are Aura, a fast, warm, Siri-like AI personal assistant.
Current local date and time: ${nowStr}.

Language rule:
- Fluently understand English, Hindi, and Hinglish (e.g., "kal subah 8 baje reminder lagao", "Mom ko call karo", "call uthao", "WhatsApp kholo", "Rahul ko message bhejo", "flashlight on karo").
- Reply in the same language/style the user used (natural Hinglish if they used Hinglish, crisp English if they used English).
- Keep replies concise (1-2 natural spoken sentences), friendly, and direct—like Siri. Never use markdown asterisks or bullet points in your reply.

User's current tasks:
${tasksSummary}

User's active reminders:
${remindersSummary}

User's saved contacts:
${contactsSummary}

Phone state:
- Call status: ${callInfo}
- Flashlight: ${safeContext.deviceState.flashlight ? 'ON' : 'OFF'}, Wi-Fi: ${safeContext.deviceState.wifi ? 'ON' : 'OFF'}, Bluetooth: ${safeContext.deviceState.bluetooth ? 'ON' : 'OFF'}, DND: ${safeContext.deviceState.dnd ? 'ON' : 'OFF'}, Volume: ${safeContext.deviceState.volume}%

Tool rules:
- Always invoke the matching function tool immediately when the user wants to add/complete/delete a task, set a reminder, make a call, answer/decline/end a call, send a message, open an app, or toggle phone settings.
- Map Hinglish intents accurately: "call uthao"/"pick up" -> manage_call(action: "answer"); "call kaato"/"end call"/"decline" -> manage_call(action: "decline"); "WhatsApp kholo" -> open_app(appName: "WhatsApp").
- For set_reminder, compute delayMinutes accurately relative to current time (${nowStr}) with minimum 1 minute.
      `.trim();

      const contents: Content[] = history.slice(-8).map((turn) => ({
        role: turn.isUser ? 'user' : 'model',
        parts: [{ text: turn.text }],
      }));
      contents.push({
        role: 'user',
        parts: [{ text: userMessage }],
      });

      const executedToolCalls: Array<{ name: string; args: Record<string, unknown> }> = [];
      let replyText = '';

      for (let step = 0; step < 3; step++) {
        const response = await ai.models.generateContent({
          model: MODEL_NAME,
          contents,
          config: {
            systemInstruction,
            tools: [{ functionDeclarations: assistantTools }],
          },
        });

        const candidateContent = response.candidates?.[0]?.content;
        const calls = response.functionCalls;

        if (!calls || calls.length === 0) {
          replyText = (response.text ?? '').trim();
          break;
        }

        if (candidateContent) {
          contents.push(candidateContent);
        }

        const functionResponseParts = calls.map((call) => {
          const fnName = call.name ?? 'unknown';
          const fnArgs = (call.args ?? {}) as Record<string, unknown>;
          executedToolCalls.push({ name: fnName, args: fnArgs });

          const resultObj = simulateToolPreviewResult(fnName, fnArgs, safeContext);
          return {
            functionResponse: {
              id: call.id,
              name: fnName,
              response: resultObj,
            },
          };
        });

        contents.push({
          role: 'user',
          parts: functionResponseParts,
        });
      }

      if (!replyText) {
        replyText = executedToolCalls.length > 0 ? 'Done.' : 'I am ready to help.';
      }

      res.json({
        replyText,
        toolCalls: executedToolCalls,
      });
    } catch (error: unknown) {
      const errObj = error as { status?: number; message?: string };
      const msg = errObj?.message || 'Unable to process request with Gemini.';
      const statusCode =
        typeof errObj?.status === 'number' && errObj.status >= 400 && errObj.status < 600
          ? errObj.status
          : 500;
      res.status(statusCode).json({ error: msg });
    }
  });

  if (process.env.NODE_ENV === 'production') {
    const distDir = path.resolve(__dirname, 'dist');
    app.use(express.static(distDir));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  } else {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Aura Assistant running on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});

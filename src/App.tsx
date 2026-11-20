import { useCallback, useEffect, useRef, useState } from 'react';
import {
  AlarmClock,
  CheckCircle2,
  Flashlight,
  MicOff,
  PhoneCall,
  PhoneOff,
  Smartphone,
  Sparkles,
  Volume2,
} from 'lucide-react';
import { AssistantPanel } from './components/AssistantPanel';
import { PhoneHubPanel } from './components/PhoneHubPanel';
import { TasksRemindersPanel } from './components/TasksRemindersPanel';
import type {
  ActionLogItem,
  ContactItem,
  ConversationEntry,
  ExecutedToolCard,
  InstalledAppItem,
  PhoneDeviceState,
  ReminderItem,
  TaskItem,
} from './types';

type NavTab = 'assistant' | 'tasks' | 'phone';

const DEFAULT_CONTACTS: ContactItem[] = [
  { id: 'c1', name: 'Mom', phone: '+91 98765 43210', relation: 'Mother' },
  { id: 'c2', name: 'Rahul', phone: '+91 98111 22334', relation: 'Friend' },
  { id: 'c3', name: 'Office Desk', phone: '+91 11 4050 9000', relation: 'Work' },
];

const DEFAULT_APPS: InstalledAppItem[] = [
  { id: 'whatsapp', name: 'WhatsApp', category: 'Messaging', launchUrl: 'https://web.whatsapp.com' },
  { id: 'youtube', name: 'YouTube', category: 'Video', launchUrl: 'https://www.youtube.com' },
  { id: 'maps', name: 'Maps', category: 'Navigation', launchUrl: 'https://maps.google.com' },
  { id: 'gmail', name: 'Gmail', category: 'Mail', launchUrl: 'https://mail.google.com' },
  { id: 'spotify', name: 'Spotify', category: 'Music', launchUrl: 'https://open.spotify.com' },
  { id: 'instagram', name: 'Instagram', category: 'Social', launchUrl: 'https://www.instagram.com' },
  { id: 'chrome', name: 'Chrome', category: 'Browser', launchUrl: 'https://www.google.com' },
  { id: 'calendar', name: 'Calendar', category: 'Schedule', launchUrl: 'https://calendar.google.com' },
  { id: 'clock', name: 'Clock', category: 'Utility' },
  { id: 'settings', name: 'Settings', category: 'System' },
];

const DEFAULT_DEVICE_STATE: PhoneDeviceState = {
  flashlight: false,
  wifi: true,
  bluetooth: true,
  dnd: false,
  volume: 75,
  activeApp: null,
  call: {
    status: 'idle',
    contactName: '',
    phoneNumber: '',
    isMuted: false,
    isSpeaker: false,
  },
};

function loadStored<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function playSoftChime() {
  try {
    const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(660, ctx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.18);
    gain.gain.setValueAtTime(0.08, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.45);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.46);
  } catch {
    // Ignore audio context restriction before user gesture
  }
}

interface SpeechRecognitionAlternativeLike {
  transcript: string;
}

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  0: SpeechRecognitionAlternativeLike;
}

interface SpeechRecognitionEventLike {
  results: ArrayLike<SpeechRecognitionResultLike>;
}

interface SpeechRecognitionErrorEventLike {
  error: string;
}

interface SpeechRecognitionInstance {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  onresult: ((ev: SpeechRecognitionEventLike) => void) | null;
  onerror: ((ev: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<NavTab>('assistant');
  const [tasks, setTasks] = useState<TaskItem[]>(() => loadStored('aura_tasks', []));
  const [reminders, setReminders] = useState<ReminderItem[]>(() => loadStored('aura_reminders', []));
  const [contacts, setContacts] = useState<ContactItem[]>(() =>
    loadStored('aura_contacts', DEFAULT_CONTACTS)
  );
  const [actionLogs, setActionLogs] = useState<ActionLogItem[]>(() =>
    loadStored('aura_action_logs', [])
  );
  const [deviceState, setDeviceState] = useState<PhoneDeviceState>(DEFAULT_DEVICE_STATE);

  const [messages, setMessages] = useState<ConversationEntry[]>([]);
  const [isListening, setIsListening] = useState(false);
  const [isThinking, setIsThinking] = useState(false);
  const [audioLevel, setAudioLevel] = useState(0);
  const [partialTranscript, setPartialTranscript] = useState('');
  const [isVoiceOutputEnabled, setIsVoiceOutputEnabled] = useState(true);
  const [statusBanner, setStatusBanner] = useState<string | null>(null);
  const [firedAlert, setFiredAlert] = useState<ReminderItem | null>(null);
  const [callSeconds, setCallSeconds] = useState(0);

  const recognitionRef = useRef<SpeechRecognitionInstance | null>(null);
  const micStreamRef = useRef<MediaStream | null>(null);
  const micAnimRef = useRef<number>(0);

  useEffect(() => {
    localStorage.setItem('aura_tasks', JSON.stringify(tasks));
  }, [tasks]);

  useEffect(() => {
    localStorage.setItem('aura_reminders', JSON.stringify(reminders));
  }, [reminders]);

  useEffect(() => {
    localStorage.setItem('aura_contacts', JSON.stringify(contacts));
  }, [contacts]);

  useEffect(() => {
    localStorage.setItem('aura_action_logs', JSON.stringify(actionLogs));
  }, [actionLogs]);

  // Live reminder trigger loop
  useEffect(() => {
    const interval = window.setInterval(() => {
      const now = Date.now();
      setReminders((prev) => {
        let changed = false;
        const updated = prev.map((r) => {
          if (!r.isFired && !r.isDismissed && r.triggerAt <= now) {
            changed = true;
            setFiredAlert(r);
            playSoftChime();
            if ('Notification' in window && Notification.permission === 'granted') {
              new Notification(`Aura Reminder: ${r.title}`, {
                body: `Scheduled for ${r.timeLabel}`,
              });
            }
            return { ...r, isFired: true };
          }
          return r;
        });
        return changed ? updated : prev;
      });
    }, 1000);
    return () => window.clearInterval(interval);
  }, []);

  // Active call duration timer
  useEffect(() => {
    if (deviceState.call.status !== 'active') {
      setCallSeconds(0);
      return;
    }
    const timer = window.setInterval(() => {
      setCallSeconds((s) => s + 1);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [deviceState.call.status]);

  const appendLog = useCallback(
    (actionType: ActionLogItem['actionType'], title: string, subtitle: string) => {
      setActionLogs((prev) => [
        {
          id: `${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
          actionType,
          title,
          subtitle,
          timestamp: Date.now(),
        },
        ...prev.slice(0, 24),
      ]);
    },
    []
  );

  const speakReply = useCallback(
    (text: string) => {
      if (!isVoiceOutputEnabled || !('speechSynthesis' in window) || !text.trim()) return;
      try {
        window.speechSynthesis.cancel();
        const utter = new SpeechSynthesisUtterance(text);
        utter.lang = 'en-IN';
        utter.rate = 1.04;
        window.speechSynthesis.speak(utter);
      } catch {
        // Speech synthesis unavailable
      }
    },
    [isVoiceOutputEnabled]
  );

  const resolveContact = useCallback(
    (query: string): ContactItem | undefined => {
      const q = query.trim().toLowerCase();
      if (!q) return undefined;
      return contacts.find(
        (c) =>
          c.name.toLowerCase().includes(q) ||
          (c.relation && c.relation.toLowerCase().includes(q))
      );
    },
    [contacts]
  );

  const applyToolCall = useCallback(
    (toolName: string, args: Record<string, unknown>): ExecutedToolCard => {
      const id = `${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;

      switch (toolName) {
        case 'add_task': {
          const title = String(args.title ?? 'Untitled Task').trim();
          const category = String(args.category ?? 'Personal');
          const rawPriority = String(args.priority ?? 'Normal');
          const priority: TaskItem['priority'] =
            rawPriority === 'High' || rawPriority === 'Low' ? rawPriority : 'Normal';
          const dueLabel = String(args.dueLabel ?? 'Today');

          const newTask: TaskItem = {
            id,
            title,
            category,
            priority,
            dueLabel,
            isCompleted: false,
            createdAt: Date.now(),
          };
          setTasks((prev) => [newTask, ...prev]);
          appendLog('TASK', `Added task: ${title}`, `${category} · ${dueLabel}`);
          return {
            id,
            toolName,
            title,
            subtitle: `${category} · ${dueLabel} · ${priority}`,
            success: true,
          };
        }

        case 'complete_task': {
          const q = String(args.taskTitle ?? '').toLowerCase();
          let matchedTitle = '';
          setTasks((prev) =>
            prev.map((t) => {
              if (!matchedTitle && t.title.toLowerCase().includes(q)) {
                matchedTitle = t.title;
                return { ...t, isCompleted: true };
              }
              return t;
            })
          );
          return {
            id,
            toolName,
            title: matchedTitle || String(args.taskTitle ?? 'Task'),
            subtitle: matchedTitle ? 'Marked complete' : 'Task updated',
            success: true,
          };
        }

        case 'delete_task': {
          const q = String(args.taskTitle ?? '').toLowerCase();
          setTasks((prev) => prev.filter((t) => !t.title.toLowerCase().includes(q)));
          return {
            id,
            toolName,
            title: String(args.taskTitle ?? 'Task'),
            subtitle: 'Removed from task list',
            success: true,
          };
        }

        case 'set_reminder': {
          const title = String(args.title ?? 'Reminder').trim();
          const delayMins = Math.max(1, Number(args.delayMinutes ?? 10));
          const triggerAt = Date.now() + delayMins * 60_000;
          const timeFormatted = new Date(triggerAt).toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit',
          });
          const label = String(args.timeLabel ?? `In ${delayMins} min`);

          if ('Notification' in window && Notification.permission === 'default') {
            void Notification.requestPermission();
          }

          const reminder: ReminderItem = {
            id,
            title,
            triggerAt,
            timeLabel: `${label} (${timeFormatted})`,
            isFired: false,
            isDismissed: false,
            createdAt: Date.now(),
          };
          setReminders((prev) => [reminder, ...prev]);
          appendLog('REMINDER', title, `Scheduled · ${reminder.timeLabel}`);
          return {
            id,
            toolName,
            title,
            subtitle: `Scheduled · ${reminder.timeLabel}`,
            success: true,
          };
        }

        case 'make_call': {
          const rawTarget = String(args.contactNameOrNumber ?? '').trim();
          const contact = resolveContact(rawTarget);
          const displayName = contact?.name ?? rawTarget;
          const phone = contact?.phone ?? rawTarget;
          const telDigits = phone.replace(/[^+\d]/g, '');

          setDeviceState((prev) => ({
            ...prev,
            call: {
              status: 'dialing',
              contactName: displayName,
              phoneNumber: phone,
              isMuted: false,
              isSpeaker: false,
            },
          }));
          appendLog('CALL', `Calling ${displayName}`, phone);

          return {
            id,
            toolName,
            title: displayName,
            subtitle: `Dialing ${phone}`,
            success: true,
            actionUrl: telDigits ? `tel:${telDigits}` : undefined,
            actionLabel: 'Dial on Phone',
          };
        }

        case 'manage_call': {
          const action = String(args.action ?? 'answer').toLowerCase();
          if (action === 'answer' || action === 'accept' || action === 'uthao') {
            const wasIncoming = deviceState.call.status === 'incoming';
            if (wasIncoming) {
              setDeviceState((prev) => ({
                ...prev,
                call: { ...prev.call, status: 'active', startedAt: Date.now() },
              }));
              appendLog('CALL_CONTROL', 'Answered Call', deviceState.call.contactName);
              return {
                id,
                toolName,
                title: `Connected · ${deviceState.call.contactName}`,
                subtitle: 'Incoming call answered hands-free',
                success: true,
              };
            }
            return {
              id,
              toolName,
              title: 'No Incoming Call',
              subtitle: 'Simulate an incoming call in Phone Hub to test hands-free pickup',
              success: false,
            };
          }

          if (action === 'decline' || action === 'end' || action === 'kaato') {
            setDeviceState((prev) => ({
              ...prev,
              call: {
                status: 'idle',
                contactName: '',
                phoneNumber: '',
                isMuted: false,
                isSpeaker: false,
              },
            }));
            appendLog('CALL_CONTROL', 'Ended Call', 'Call disconnected');
            return {
              id,
              toolName,
              title: 'Call Ended',
              subtitle: 'Call disconnected',
              success: true,
            };
          }

          if (action === 'speaker') {
            setDeviceState((prev) => ({
              ...prev,
              call: { ...prev.call, isSpeaker: !prev.call.isSpeaker },
            }));
            return {
              id,
              toolName,
              title: 'Speaker Toggled',
              subtitle: 'Audio routing updated',
              success: true,
            };
          }

          setDeviceState((prev) => ({
            ...prev,
            call: { ...prev.call, isMuted: !prev.call.isMuted },
          }));
          return {
            id,
            toolName,
            title: 'Microphone Toggled',
            subtitle: 'Call mute state updated',
            success: true,
          };
        }

        case 'send_message': {
          const rawRecipient = String(args.recipient ?? '').trim();
          const body = String(args.message ?? '').trim();
          const channel = String(args.channel ?? 'sms').toLowerCase();
          const contact = resolveContact(rawRecipient);
          const displayName = contact?.name ?? rawRecipient;
          const phone = (contact?.phone ?? rawRecipient).replace(/[^+\d]/g, '');

          const isWhatsApp = channel.includes('whatsapp');
          const actionUrl = isWhatsApp
            ? `https://wa.me/${phone.replace(/^\+/, '')}?text=${encodeURIComponent(body)}`
            : `sms:${phone}?body=${encodeURIComponent(body)}`;

          appendLog('MESSAGE', `Message to ${displayName}`, body);
          return {
            id,
            toolName,
            title: `To ${displayName} (${isWhatsApp ? 'WhatsApp' : 'SMS'})`,
            subtitle: body,
            success: true,
            actionUrl,
            actionLabel: isWhatsApp ? 'Open WhatsApp' : 'Open Messages',
          };
        }

        case 'open_app': {
          const appQuery = String(args.appName ?? '').trim();
          const matched = DEFAULT_APPS.find((a) =>
            a.name.toLowerCase().includes(appQuery.toLowerCase())
          );
          const appLabel = matched?.name ?? appQuery;
          setDeviceState((prev) => ({ ...prev, activeApp: appLabel }));
          appendLog('OPEN_APP', `Opened ${appLabel}`, matched?.category ?? 'App');
          return {
            id,
            toolName,
            title: appLabel,
            subtitle: matched?.launchUrl ? 'Ready to launch' : 'Activated in Phone Hub',
            success: true,
            actionUrl: matched?.launchUrl,
            actionLabel: matched?.launchUrl ? `Launch ${appLabel}` : undefined,
          };
        }

        case 'control_device': {
          const setting = String(args.setting ?? '').toLowerCase();
          const enabled = args.enabled !== undefined ? Boolean(args.enabled) : true;
          const volumeVal =
            typeof args.volumeLevel === 'number'
              ? Math.max(0, Math.min(100, args.volumeLevel))
              : undefined;

          setDeviceState((prev) => {
            if (setting === 'flashlight' || setting === 'torch') {
              return { ...prev, flashlight: enabled };
            }
            if (setting === 'wifi' || setting === 'wi-fi') {
              return { ...prev, wifi: enabled };
            }
            if (setting === 'bluetooth') {
              return { ...prev, bluetooth: enabled };
            }
            if (setting === 'dnd') {
              return { ...prev, dnd: enabled };
            }
            if (setting === 'volume' && volumeVal !== undefined) {
              return { ...prev, volume: volumeVal };
            }
            return prev;
          });
          appendLog(
            'DEVICE',
            `${setting.toUpperCase()}`,
            setting === 'volume' && volumeVal !== undefined
              ? `Volume set to ${volumeVal}%`
              : enabled
                ? 'Turned ON'
                : 'Turned OFF'
          );
          return {
            id,
            toolName,
            title: setting.charAt(0).toUpperCase() + setting.slice(1),
            subtitle:
              setting === 'volume' && volumeVal !== undefined
                ? `Set to ${volumeVal}%`
                : enabled
                  ? 'Turned ON'
                  : 'Turned OFF',
            success: true,
          };
        }

        default:
          return {
            id,
            toolName,
            title: toolName,
            subtitle: 'Completed',
            success: true,
          };
      }
    },
    [appendLog, deviceState.call.contactName, deviceState.call.status, resolveContact]
  );

  const submitCommand = useCallback(
    async (rawText: string) => {
      const text = rawText.trim();
      if (!text || isThinking) return;

      setStatusBanner(null);
      const userMsg: ConversationEntry = {
        id: `u_${Date.now()}`,
        isUser: true,
        text,
        timestamp: Date.now(),
      };
      setMessages((prev) => [...prev, userMsg]);
      setIsThinking(true);

      try {
        const history = messages
          .filter((m) => !m.isError)
          .slice(-8)
          .map((m) => ({ isUser: m.isUser, text: m.text }));

        const response = await fetch('/api/assistant', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message: text,
            history,
            context: {
              tasks,
              reminders,
              contacts,
              deviceState,
            },
          }),
        });

        const data = (await response.json()) as {
          replyText?: string;
          toolCalls?: Array<{ name: string; args: Record<string, unknown> }>;
          error?: string;
        };

        if (!response.ok) {
          throw new Error(data.error || `Request failed (${response.status})`);
        }

        const cards = (data.toolCalls ?? []).map((call) =>
          applyToolCall(call.name, call.args ?? {})
        );
        const reply = data.replyText || 'Done.';

        setMessages((prev) => [
          ...prev,
          {
            id: `a_${Date.now()}`,
            isUser: false,
            text: reply,
            cards,
            timestamp: Date.now(),
          },
        ]);
        speakReply(reply);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Unable to reach Aura right now.';
        setMessages((prev) => [
          ...prev,
          {
            id: `e_${Date.now()}`,
            isUser: false,
            text: msg,
            isError: true,
            timestamp: Date.now(),
          },
        ]);
      } finally {
        setIsThinking(false);
      }
    },
    [applyToolCall, contacts, deviceState, isThinking, messages, reminders, speakReply, tasks]
  );

  const stopMicMeter = useCallback(() => {
    if (micAnimRef.current) {
      cancelAnimationFrame(micAnimRef.current);
      micAnimRef.current = 0;
    }
    if (micStreamRef.current) {
      micStreamRef.current.getTracks().forEach((t) => t.stop());
      micStreamRef.current = null;
    }
    setAudioLevel(0);
  }, []);

  const stopListening = useCallback(() => {
    setIsListening(false);
    setPartialTranscript('');
    try {
      recognitionRef.current?.stop();
    } catch {
      // Ignore
    }
    stopMicMeter();
  }, [stopMicMeter]);

  const startListening = useCallback(async () => {
    const w = window as unknown as {
      SpeechRecognition?: new () => SpeechRecognitionInstance;
      webkitSpeechRecognition?: new () => SpeechRecognitionInstance;
    };
    const RecognitionCtor = w.SpeechRecognition || w.webkitSpeechRecognition;

    if (!RecognitionCtor) {
      setStatusBanner(
        'Voice recognition is not supported in this browser. Type your command in the input bar below.'
      );
      return;
    }

    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
    setStatusBanner(null);

    // Optional live mic RMS meter for organic orb ripples
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      micStreamRef.current = stream;
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const audioCtx = new AudioCtx();
      const source = audioCtx.createMediaStreamSource(stream);
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 256;
      source.connect(analyser);
      const buffer = new Uint8Array(analyser.frequencyBinCount);

      const tick = () => {
        if (!micStreamRef.current) return;
        analyser.getByteFrequencyData(buffer);
        let sum = 0;
        for (let i = 0; i < buffer.length; i++) sum += buffer[i];
        const avg = sum / buffer.length / 128;
        setAudioLevel(Math.min(1, avg));
        micAnimRef.current = requestAnimationFrame(tick);
      };
      tick();
    } catch {
      // Proceed even if AnalyserNode meter isn't available
    }

    const recognition = new RecognitionCtor();
    recognition.lang = 'en-IN';
    recognition.interimResults = true;
    recognition.continuous = false;

    let finalText = '';

    recognition.onresult = (ev) => {
      let interim = '';
      for (let i = 0; i < ev.results.length; i++) {
        const res = ev.results[i];
        const transcript = res[0]?.transcript ?? '';
        if (res.isFinal) {
          finalText += transcript + ' ';
        } else {
          interim += transcript;
        }
      }
      setPartialTranscript((finalText + interim).trim());
    };

    recognition.onerror = (ev) => {
      if (ev.error === 'not-allowed') {
        setStatusBanner('Microphone permission was denied. Allow mic access or type below.');
      } else if (ev.error !== 'aborted') {
        setStatusBanner(`Voice capture error: ${ev.error}`);
      }
      stopListening();
    };

    recognition.onend = () => {
      const captured = finalText.trim();
      stopListening();
      if (captured) {
        void submitCommand(captured);
      }
    };

    recognitionRef.current = recognition;
    setIsListening(true);
    try {
      recognition.start();
    } catch {
      stopListening();
    }
  }, [stopListening, submitCommand]);

  const handleToggleListening = useCallback(() => {
    if (isListening) {
      stopListening();
    } else {
      void startListening();
    }
  }, [isListening, startListening, stopListening]);

  const handleManageCallDirect = (action: 'answer' | 'decline' | 'end' | 'mute' | 'speaker') => {
    const card = applyToolCall('manage_call', { action });
    if (!card.success) {
      setStatusBanner(card.subtitle);
    }
  };

  const pendingTaskCount = tasks.filter((t) => !t.isCompleted).length;
  const activeReminderCount = reminders.filter((r) => !r.isDismissed && !r.isFired).length;

  return (
    <div
      className={`h-dvh w-full flex flex-col lg:flex-row bg-[#0A0D14] text-[#F4F6FA] overflow-hidden ${
        deviceState.flashlight ? 'ring-4 ring-inset ring-[#F5A524]/70' : ''
      }`}
    >
      {/* Desktop Left Navigation Rail */}
      <aside className="hidden lg:flex lg:flex-col lg:w-[260px] shrink-0 border-r border-white/[0.08] bg-[#0D1119] p-5 justify-between">
        <div className="space-y-8">
          <div className="px-2">
            <span className="text-xl font-semibold tracking-tight font-display text-[#F4F6FA]">
              Aura
            </span>
          </div>

          <nav className="space-y-1.5" aria-label="Primary Navigation">
            {(
              [
                { id: 'assistant', label: 'Voice Assistant', icon: Sparkles },
                { id: 'tasks', label: 'Tasks & Reminders', icon: CheckCircle2 },
                { id: 'phone', label: 'Phone Hub', icon: Smartphone },
              ] as const
            ).map((item) => {
              const Icon = item.icon;
              const selected = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setActiveTab(item.id)}
                  className={`w-full min-h-[46px] px-3.5 rounded-xl flex items-center gap-3 text-sm font-medium transition-colors cursor-pointer whitespace-nowrap ${
                    selected
                      ? 'bg-[#4C7DFF]/18 text-[#F4F6FA] border border-[#4C7DFF]/40'
                      : 'text-[#9AA6BD] hover:text-[#F4F6FA] hover:bg-white/[0.04] border border-transparent'
                  }`}
                >
                  <Icon className={`w-4 h-4 ${selected ? 'text-[#4C7DFF]' : ''}`} />
                  <span>{item.label}</span>
                </button>
              );
            })}
          </nav>
        </div>

        <div className="rounded-2xl bg-[#121722] border border-white/[0.08] p-4 space-y-2.5">
          <p className="text-xs font-medium text-[#6B778F]">Today at a glance</p>
          <div className="flex items-center justify-between text-xs">
            <span className="text-[#9AA6BD]">Pending tasks</span>
            <span className="font-mono tabular-nums text-[#F4F6FA]">{pendingTaskCount}</span>
          </div>
          <div className="flex items-center justify-between text-xs">
            <span className="text-[#9AA6BD]">Active reminders</span>
            <span className="font-mono tabular-nums text-[#F4F6FA]">{activeReminderCount}</span>
          </div>
          <div className="flex items-center justify-between text-xs">
            <span className="text-[#9AA6BD]">Flashlight</span>
            <span className="font-mono text-[#25C2E3]">
              {deviceState.flashlight ? 'ON' : 'OFF'}
            </span>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col min-w-0 min-h-0">
        {/* Live Flashlight Indicator Strip */}
        {deviceState.flashlight && (
          <div className="px-5 py-2 bg-[#F5A524]/15 border-b border-[#F5A524]/40 flex items-center justify-between shrink-0">
            <div className="flex items-center gap-2 text-xs font-medium text-[#F5A524]">
              <Flashlight className="w-4 h-4" />
              <span>Flashlight is ON</span>
            </div>
            <button
              type="button"
              onClick={() => applyToolCall('control_device', { setting: 'flashlight', enabled: false })}
              className="text-xs font-medium text-[#F4F6FA] hover:underline cursor-pointer"
            >
              Turn Off
            </button>
          </div>
        )}

        {/* Live Triggered Reminder Alert */}
        {firedAlert && (
          <div className="px-5 py-3 bg-[#F5A524]/15 border-b border-[#F5A524]/40 flex items-center justify-between gap-3 shrink-0">
            <div className="flex items-center gap-2.5 min-w-0">
              <AlarmClock className="w-4 h-4 text-[#F5A524] shrink-0" />
              <div className="min-w-0">
                <p className="text-sm font-medium text-[#F4F6FA] truncate">
                  Reminder: {firedAlert.title}
                </p>
                <p className="text-xs text-[#9AA6BD] tabular-nums">{firedAlert.timeLabel}</p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => {
                setReminders((prev) =>
                  prev.map((r) => (r.id === firedAlert.id ? { ...r, isDismissed: true } : r))
                );
                setFiredAlert(null);
              }}
              className="min-h-[36px] px-3 rounded-lg bg-[#121722] border border-white/[0.1] text-xs font-medium text-[#F4F6FA] whitespace-nowrap cursor-pointer"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Live Incoming / Active Call Banner */}
        {deviceState.call.status !== 'idle' && (
          <div className="px-5 py-3 bg-[#181F2D] border-b border-[#2ECC8F]/40 flex items-center justify-between gap-3 shrink-0">
            <div className="min-w-0">
              <p className="text-sm font-medium text-[#F4F6FA] truncate">
                {deviceState.call.status === 'incoming'
                  ? `Incoming Call · ${deviceState.call.contactName}`
                  : deviceState.call.status === 'dialing'
                    ? `Dialing · ${deviceState.call.contactName}`
                    : `In Call · ${deviceState.call.contactName}`}
              </p>
              <p className="text-xs text-[#9AA6BD] tabular-nums">
                {deviceState.call.phoneNumber}
                {deviceState.call.status === 'active' &&
                  ` · ${String(Math.floor(callSeconds / 60)).padStart(2, '0')}:${String(
                    callSeconds % 60
                  ).padStart(2, '0')}`}
              </p>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              {deviceState.call.status === 'incoming' ? (
                <button
                  type="button"
                  onClick={() => handleManageCallDirect('answer')}
                  className="min-h-[38px] px-3.5 rounded-xl bg-[#2ECC8F] text-white text-xs font-semibold flex items-center gap-1.5 cursor-pointer"
                >
                  <PhoneCall className="w-3.5 h-3.5" />
                  <span>Answer</span>
                </button>
              ) : (
                <>
                  <button
                    type="button"
                    onClick={() => handleManageCallDirect('mute')}
                    aria-label="Mute call"
                    className={`min-h-[38px] min-w-[38px] rounded-xl border border-white/[0.1] flex items-center justify-center cursor-pointer ${
                      deviceState.call.isMuted ? 'bg-[#4C7DFF] text-white' : 'bg-[#121722] text-[#9AA6BD]'
                    }`}
                  >
                    <MicOff className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => handleManageCallDirect('speaker')}
                    aria-label="Toggle speaker"
                    className={`min-h-[38px] min-w-[38px] rounded-xl border border-white/[0.1] flex items-center justify-center cursor-pointer ${
                      deviceState.call.isSpeaker ? 'bg-[#4C7DFF] text-white' : 'bg-[#121722] text-[#9AA6BD]'
                    }`}
                  >
                    <Volume2 className="w-4 h-4" />
                  </button>
                </>
              )}
              <button
                type="button"
                onClick={() => handleManageCallDirect('decline')}
                className="min-h-[38px] px-3.5 rounded-xl bg-[#F25C69] text-white text-xs font-semibold flex items-center gap-1.5 cursor-pointer"
              >
                <PhoneOff className="w-3.5 h-3.5" />
                <span>{deviceState.call.status === 'incoming' ? 'Decline' : 'End'}</span>
              </button>
            </div>
          </div>
        )}

        {/* Active Tab Viewport */}
        <div className="flex-1 min-h-0">
          {activeTab === 'assistant' && (
            <AssistantPanel
              messages={messages}
              pendingTaskCount={pendingTaskCount}
              activeReminderCount={activeReminderCount}
              isListening={isListening}
              isThinking={isThinking}
              audioLevel={audioLevel}
              partialTranscript={partialTranscript}
              isVoiceOutputEnabled={isVoiceOutputEnabled}
              statusBanner={statusBanner}
              hasIncomingCall={deviceState.call.status === 'incoming'}
              onToggleListening={handleToggleListening}
              onSubmitCommand={(text) => void submitCommand(text)}
              onToggleVoiceOutput={() => {
                setIsVoiceOutputEnabled((v) => !v);
                if ('speechSynthesis' in window) window.speechSynthesis.cancel();
              }}
              onClearConversation={() => setMessages([])}
              onAnswerCall={() => handleManageCallDirect('answer')}
              onDismissBanner={() => setStatusBanner(null)}
            />
          )}

          {activeTab === 'tasks' && (
            <TasksRemindersPanel
              tasks={tasks}
              reminders={reminders}
              onAddTask={(title, category, priority, dueLabel) => {
                applyToolCall('add_task', { title, category, priority, dueLabel });
              }}
              onToggleTask={(id) =>
                setTasks((prev) =>
                  prev.map((t) => (t.id === id ? { ...t, isCompleted: !t.isCompleted } : t))
                )
              }
              onDeleteTask={(id) => setTasks((prev) => prev.filter((t) => t.id !== id))}
              onAddReminder={(title, delayMinutes) => {
                applyToolCall('set_reminder', {
                  title,
                  delayMinutes,
                  timeLabel: `In ${delayMinutes} min`,
                });
              }}
              onDismissReminder={(id) =>
                setReminders((prev) =>
                  prev.map((r) => (r.id === id ? { ...r, isDismissed: true } : r))
                )
              }
            />
          )}

          {activeTab === 'phone' && (
            <PhoneHubPanel
              deviceState={deviceState}
              contacts={contacts}
              apps={DEFAULT_APPS}
              actionLogs={actionLogs}
              onToggleSetting={(setting) => {
                const next = !deviceState[setting];
                applyToolCall('control_device', { setting, enabled: next });
              }}
              onVolumeChange={(val) =>
                setDeviceState((prev) => ({ ...prev, volume: val }))
              }
              onSimulateIncomingCall={() => {
                const caller = contacts[0] ?? { name: 'Mom', phone: '+91 98765 43210' };
                playSoftChime();
                setDeviceState((prev) => ({
                  ...prev,
                  call: {
                    status: 'incoming',
                    contactName: caller.name,
                    phoneNumber: caller.phone,
                    isMuted: false,
                    isSpeaker: false,
                  },
                }));
              }}
              onManageCall={handleManageCallDirect}
              onDialNumber={(target) => {
                const card = applyToolCall('make_call', { contactNameOrNumber: target });
                setStatusBanner(card.subtitle);
              }}
              onSendMessage={(recipient, body, channel) => {
                const card = applyToolCall('send_message', {
                  recipient,
                  message: body,
                  channel,
                });
                if (card.actionUrl) {
                  const link = document.createElement('a');
                  link.href = card.actionUrl;
                  link.target = '_blank';
                  link.rel = 'noopener noreferrer';
                  link.click();
                }
              }}
              onOpenApp={(appName) => {
                const card = applyToolCall('open_app', { appName });
                if (card.actionUrl) {
                  const link = document.createElement('a');
                  link.href = card.actionUrl;
                  link.target = '_blank';
                  link.rel = 'noopener noreferrer';
                  link.click();
                }
              }}
              onAddContact={(name, phone, relation) => {
                setContacts((prev) => [
                  ...prev,
                  { id: `c_${Date.now()}`, name, phone, relation },
                ]);
              }}
              onClearLogs={() => setActionLogs([])}
            />
          )}
        </div>

        {/* Mobile Bottom Tab Bar */}
        <nav
          aria-label="Mobile Navigation"
          className="lg:hidden grid grid-cols-3 h-14 border-t border-white/[0.08] bg-[#0D1119] shrink-0"
        >
          {(
            [
              { id: 'assistant', label: 'Assistant', icon: Sparkles },
              { id: 'tasks', label: 'Tasks', icon: CheckCircle2 },
              { id: 'phone', label: 'Phone Hub', icon: Smartphone },
            ] as const
          ).map((item) => {
            const Icon = item.icon;
            const selected = activeTab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setActiveTab(item.id)}
                className={`flex flex-col items-center justify-center gap-0.5 text-[11px] font-medium transition-colors cursor-pointer ${
                  selected ? 'text-[#4C7DFF]' : 'text-[#6B778F]'
                }`}
              >
                <Icon className="w-5 h-5" />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
      </main>
    </div>
  );
}

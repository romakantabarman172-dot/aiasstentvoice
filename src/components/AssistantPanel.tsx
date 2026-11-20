import { useEffect, useRef, useState } from 'react';
import {
  AlarmClock,
  AlertCircle,
  AppWindow,
  CheckCircle2,
  ExternalLink,
  Flashlight,
  Keyboard,
  MessageSquare,
  Phone,
  PhoneIncoming,
  Send,
  Trash2,
  Volume2,
  VolumeX,
} from 'lucide-react';
import type { ConversationEntry, ExecutedToolCard } from '../types';
import { VoiceOrb } from './VoiceOrb';

interface AssistantPanelProps {
  messages: ConversationEntry[];
  pendingTaskCount: number;
  activeReminderCount: number;
  isListening: boolean;
  isThinking: boolean;
  audioLevel: number;
  partialTranscript: string;
  isVoiceOutputEnabled: boolean;
  statusBanner: string | null;
  hasIncomingCall: boolean;
  onToggleListening: () => void;
  onSubmitCommand: (text: string) => void;
  onToggleVoiceOutput: () => void;
  onClearConversation: () => void;
  onAnswerCall: () => void;
  onDismissBanner: () => void;
}

const QUICK_PROMPTS = [
  { label: '10 min baad reminder', command: '10 minute baad paani peene ka reminder lagao' },
  { label: 'Add daily task', command: 'Add a high priority task: Review project notes today' },
  { label: 'Mom ko call karo', command: 'Mom ko call karo' },
  { label: 'WhatsApp kholo', command: 'WhatsApp kholo' },
  { label: 'Message bhejo', command: 'Rahul ko WhatsApp message bhejo ki main 10 min mein aa raha hoon' },
  { label: 'Call uthao', command: 'Call uthao' },
  { label: 'Flashlight on karo', command: 'Flashlight on karo' },
];

export function AssistantPanel({
  messages,
  pendingTaskCount,
  activeReminderCount,
  isListening,
  isThinking,
  audioLevel,
  partialTranscript,
  isVoiceOutputEnabled,
  statusBanner,
  hasIncomingCall,
  onToggleListening,
  onSubmitCommand,
  onToggleVoiceOutput,
  onClearConversation,
  onAnswerCall,
  onDismissBanner,
}: AssistantPanelProps) {
  const [inputText, setInputText] = useState('');
  const [showKeyboard, setShowKeyboard] = useState(true);
  const endRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length, isThinking]);

  const now = new Date();
  const hour = now.getHours();
  const greeting =
    hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : hour < 21 ? 'Good evening' : 'Good night';
  const dateStr = now.toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  });

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim() || isThinking) return;
    onSubmitCommand(inputText);
    setInputText('');
  };

  return (
    <div className="flex flex-col h-full min-h-0">
      {/* Compact Top Bar */}
      <div className="flex items-center justify-between gap-4 px-5 py-3.5 border-b border-white/[0.08] shrink-0">
        <div className="flex items-baseline gap-2.5 min-w-0">
          <h2 className="text-lg font-semibold tracking-tight text-[#F4F6FA] whitespace-nowrap">
            Voice Assistant
          </h2>
          <span className="text-xs text-[#9AA6BD] tabular-nums whitespace-nowrap">
            · {dateStr}
          </span>
        </div>

        <div className="flex items-center gap-1 shrink-0">
          <button
            type="button"
            onClick={onToggleVoiceOutput}
            aria-label={isVoiceOutputEnabled ? 'Mute voice output' : 'Unmute voice output'}
            className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg text-[#9AA6BD] hover:text-[#F4F6FA] hover:bg-white/[0.05] transition-colors cursor-pointer"
          >
            {isVoiceOutputEnabled ? <Volume2 className="w-5 h-5" /> : <VolumeX className="w-5 h-5" />}
          </button>
          {messages.length > 0 && (
            <button
              type="button"
              onClick={onClearConversation}
              aria-label="Clear conversation"
              className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg text-[#9AA6BD] hover:text-[#F4F6FA] hover:bg-white/[0.05] transition-colors cursor-pointer"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>

      {/* Transient Status Banner */}
      {statusBanner && (
        <div className="mx-5 mt-3 px-4 py-2.5 rounded-xl bg-[#181F2D] border border-white/[0.08] flex items-center justify-between gap-3 shrink-0">
          <p className="text-xs text-[#9AA6BD]">{statusBanner}</p>
          <button
            type="button"
            onClick={onDismissBanner}
            className="text-xs font-medium text-[#25C2E3] hover:underline whitespace-nowrap cursor-pointer"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Main Conversation Stream / Welcome State */}
      <div className="flex-1 overflow-y-auto px-5 py-6">
        {messages.length === 0 ? (
          <div className="max-w-2xl mx-auto flex flex-col justify-center min-h-full py-6">
            <h1 className="text-3xl sm:text-4xl font-semibold tracking-tight text-[#F4F6FA] text-balance">
              {greeting}
            </h1>
            <p className="mt-2 text-sm text-[#9AA6BD] tabular-nums">
              {pendingTaskCount} pending tasks · {activeReminderCount} active reminders
            </p>
            <p className="mt-5 text-[15px] leading-relaxed text-[#9AA6BD] max-w-xl">
              Speak or type naturally in English or Hinglish. Aura manages your daily tasks, schedules
              timed reminders, handles calls and messages, and launches phone apps.
            </p>

            <div className="mt-8">
              <p className="text-xs font-medium text-[#6B778F] mb-3">Quick voice commands</p>
              <div className="flex flex-wrap gap-2">
                {QUICK_PROMPTS.map((item) => (
                  <button
                    key={item.label}
                    type="button"
                    onClick={() => onSubmitCommand(item.command)}
                    className="min-h-[42px] px-4 py-2 rounded-xl bg-[#121722] hover:bg-[#181F2D] border border-white/[0.08] text-sm text-[#F4F6FA] transition-colors cursor-pointer whitespace-nowrap"
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>
          </div>
        ) : (
          <div className="max-w-3xl mx-auto space-y-4">
            {messages.map((entry) => (
              <div
                key={entry.id}
                className={`flex flex-col ${entry.isUser ? 'items-end' : 'items-start'}`}
              >
                <div
                  className={`max-w-[86%] sm:max-w-[78%] rounded-2xl px-4 py-3 text-[15px] leading-relaxed border ${
                    entry.isError
                      ? 'bg-[#F25C69]/12 border-[#F25C69]/35 text-[#F4F6FA]'
                      : entry.isUser
                        ? 'bg-[#4C7DFF]/20 border-[#4C7DFF]/35 text-[#F4F6FA]'
                        : 'bg-[#121722] border-white/[0.08] text-[#F4F6FA]'
                  }`}
                >
                  <div className="flex items-start gap-2.5">
                    {entry.isError && (
                      <AlertCircle className="w-4 h-4 text-[#F25C69] shrink-0 mt-1" />
                    )}
                    <span>{entry.text}</span>
                  </div>
                </div>

                {entry.cards && entry.cards.length > 0 && (
                  <div className="mt-2.5 space-y-2 w-full max-w-[86%] sm:max-w-[78%]">
                    {entry.cards.map((card) => (
                      <ActionCard key={card.id} card={card} />
                    ))}
                  </div>
                )}
              </div>
            ))}

            {isThinking && (
              <div className="flex items-start">
                <div className="rounded-2xl px-4 py-3 bg-[#121722] border border-white/[0.08] text-sm text-[#9AA6BD]">
                  Processing your request...
                </div>
              </div>
            )}
            <div ref={endRef} />
          </div>
        )}
      </div>

      {/* Live Transcript Readout */}
      {(isListening || partialTranscript) && (
        <div className="px-6 pb-1 text-center shrink-0">
          <p className="text-sm text-[#25C2E3] truncate max-w-xl mx-auto">
            {partialTranscript || 'Listening... speak in English or Hinglish'}
          </p>
        </div>
      )}

      {/* Bottom Thumb-Zone Control Dock */}
      <div className="border-t border-white/[0.08] bg-[#0E121B]/95 backdrop-blur-md px-4 pt-3 pb-3 shrink-0">
        {showKeyboard && (
          <form onSubmit={handleSend} className="max-w-3xl mx-auto flex items-center gap-2 mb-2">
            <input
              type="text"
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder="Ask Aura (e.g., Mom ko call karo, 10 min baad reminder lagao)..."
              className="flex-1 min-h-[44px] px-4 rounded-xl bg-[#121722] border border-white/[0.08] text-sm text-[#F4F6FA] placeholder:text-[#6B778F] focus:outline-none focus:border-[#4C7DFF]"
            />
            <button
              type="submit"
              disabled={!inputText.trim() || isThinking}
              aria-label="Send command"
              className="min-h-[44px] min-w-[44px] px-4 rounded-xl bg-[#4C7DFF] hover:bg-[#3B6CEE] disabled:opacity-40 text-white flex items-center justify-center transition-colors cursor-pointer"
            >
              <Send className="w-4 h-4" />
            </button>
          </form>
        )}

        <div className="max-w-md mx-auto flex items-center justify-between">
          <button
            type="button"
            onClick={() => setShowKeyboard((v) => !v)}
            aria-label="Toggle text input"
            className={`min-h-[46px] min-w-[46px] rounded-full border border-white/[0.08] flex items-center justify-center transition-colors cursor-pointer ${
              showKeyboard
                ? 'bg-[#181F2D] text-[#4C7DFF]'
                : 'bg-[#121722] text-[#9AA6BD] hover:text-[#F4F6FA]'
            }`}
          >
            <Keyboard className="w-5 h-5" />
          </button>

          <div className="flex flex-col items-center">
            <VoiceOrb
              isListening={isListening}
              isThinking={isThinking}
              audioLevel={audioLevel}
              onClick={onToggleListening}
            />
            <span className="text-[11px] text-[#6B778F] -mt-1">
              {isListening ? 'Tap to stop' : isThinking ? 'Working...' : 'Tap to speak'}
            </span>
          </div>

          <button
            type="button"
            onClick={onAnswerCall}
            aria-label="Answer incoming call"
            className={`min-h-[46px] min-w-[46px] rounded-full border flex items-center justify-center transition-colors cursor-pointer ${
              hasIncomingCall
                ? 'bg-[#2ECC8F]/20 border-[#2ECC8F]/60 text-[#2ECC8F]'
                : 'bg-[#121722] border-white/[0.08] text-[#2ECC8F] hover:bg-[#181F2D]'
            }`}
          >
            <PhoneIncoming className="w-5 h-5" />
          </button>
        </div>
      </div>
    </div>
  );
}

function ActionCard({ card }: { card: ExecutedToolCard }) {
  const Icon = (() => {
    switch (card.toolName) {
      case 'add_task':
      case 'complete_task':
      case 'delete_task':
        return CheckCircle2;
      case 'set_reminder':
        return AlarmClock;
      case 'make_call':
      case 'manage_call':
        return Phone;
      case 'send_message':
        return MessageSquare;
      case 'open_app':
        return AppWindow;
      default:
        return Flashlight;
    }
  })();

  const iconTint = !card.success
    ? 'text-[#F25C69] bg-[#F25C69]/15'
    : card.toolName === 'set_reminder'
      ? 'text-[#F5A524] bg-[#F5A524]/15'
      : card.toolName === 'make_call' || card.toolName === 'manage_call'
        ? 'text-[#2ECC8F] bg-[#2ECC8F]/15'
        : 'text-[#25C2E3] bg-[#25C2E3]/15';

  return (
    <div className="rounded-2xl bg-[#181F2D] border border-white/[0.08] px-4 py-3 flex items-center justify-between gap-3">
      <div className="flex items-center gap-3 min-w-0">
        <div className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 ${iconTint}`}>
          <Icon className="w-4 h-4" />
        </div>
        <div className="min-w-0">
          <p className="text-sm font-medium text-[#F4F6FA] truncate">{card.title}</p>
          <p className="text-xs text-[#9AA6BD] truncate">{card.subtitle}</p>
        </div>
      </div>

      {card.actionUrl && (
        <a
          href={card.actionUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="min-h-[36px] px-3 py-1.5 rounded-lg bg-[#4C7DFF]/15 hover:bg-[#4C7DFF]/25 text-xs font-medium text-[#25C2E3] flex items-center gap-1.5 whitespace-nowrap shrink-0"
        >
          <span>{card.actionLabel ?? 'Open'}</span>
          <ExternalLink className="w-3.5 h-3.5" />
        </a>
      )}
    </div>
  );
}

export interface TaskItem {
  id: string;
  title: string;
  category: string;
  priority: 'High' | 'Normal' | 'Low';
  dueLabel: string;
  isCompleted: boolean;
  createdAt: number;
}

export interface ReminderItem {
  id: string;
  title: string;
  triggerAt: number;
  timeLabel: string;
  isFired: boolean;
  isDismissed: boolean;
  createdAt: number;
}

export interface ContactItem {
  id: string;
  name: string;
  phone: string;
  relation?: string;
}

export interface InstalledAppItem {
  id: string;
  name: string;
  category: string;
  launchUrl?: string;
}

export interface CallSessionState {
  status: 'idle' | 'incoming' | 'active' | 'dialing';
  contactName: string;
  phoneNumber: string;
  isMuted: boolean;
  isSpeaker: boolean;
  startedAt?: number;
}

export interface PhoneDeviceState {
  flashlight: boolean;
  wifi: boolean;
  bluetooth: boolean;
  dnd: boolean;
  volume: number;
  activeApp: string | null;
  call: CallSessionState;
}

export interface ActionLogItem {
  id: string;
  actionType: 'CALL' | 'CALL_CONTROL' | 'MESSAGE' | 'OPEN_APP' | 'DEVICE' | 'TASK' | 'REMINDER';
  title: string;
  subtitle: string;
  timestamp: number;
}

export interface ExecutedToolCard {
  id: string;
  toolName: string;
  title: string;
  subtitle: string;
  success: boolean;
  actionUrl?: string;
  actionLabel?: string;
}

export interface ConversationEntry {
  id: string;
  isUser: boolean;
  text: string;
  isError?: boolean;
  cards?: ExecutedToolCard[];
  timestamp: number;
}

export interface ToolActionPayload {
  toolName: string;
  args: Record<string, unknown>;
}

import { useState } from 'react';
import {
  BellOff,
  Bluetooth,
  ExternalLink,
  Flashlight,
  MessageSquare,
  Phone,
  PhoneCall,
  PhoneOff,
  Plus,
  Volume2,
  Wifi,
} from 'lucide-react';
import type {
  ActionLogItem,
  ContactItem,
  InstalledAppItem,
  PhoneDeviceState,
} from '../types';

interface PhoneHubPanelProps {
  deviceState: PhoneDeviceState;
  contacts: ContactItem[];
  apps: InstalledAppItem[];
  actionLogs: ActionLogItem[];
  onToggleSetting: (setting: 'flashlight' | 'wifi' | 'bluetooth' | 'dnd') => void;
  onVolumeChange: (val: number) => void;
  onSimulateIncomingCall: () => void;
  onManageCall: (action: 'answer' | 'decline' | 'end' | 'mute' | 'speaker') => void;
  onDialNumber: (target: string) => void;
  onSendMessage: (recipient: string, body: string, channel: 'sms' | 'whatsapp') => void;
  onOpenApp: (appName: string) => void;
  onAddContact: (name: string, phone: string, relation: string) => void;
  onClearLogs: () => void;
}

export function PhoneHubPanel({
  deviceState,
  contacts,
  apps,
  actionLogs,
  onToggleSetting,
  onVolumeChange,
  onSimulateIncomingCall,
  onManageCall,
  onDialNumber,
  onSendMessage,
  onOpenApp,
  onAddContact,
  onClearLogs,
}: PhoneHubPanelProps) {
  const [dialTarget, setDialTarget] = useState('');
  const [msgRecipient, setMsgRecipient] = useState('');
  const [msgBody, setMsgBody] = useState('');
  const [newContactName, setNewContactName] = useState('');
  const [newContactPhone, setNewContactPhone] = useState('');

  const handleDialSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!dialTarget.trim()) return;
    onDialNumber(dialTarget.trim());
  };

  const handleAddContact = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newContactName.trim() || !newContactPhone.trim()) return;
    onAddContact(newContactName.trim(), newContactPhone.trim(), '');
    setNewContactName('');
    setNewContactPhone('');
  };

  return (
    <div className="flex flex-col h-full min-h-0">
      {/* Header */}
      <div className="flex items-center justify-between gap-4 px-5 py-3.5 border-b border-white/[0.08] shrink-0">
        <div className="min-w-0">
          <h2 className="text-lg font-semibold tracking-tight text-[#F4F6FA] whitespace-nowrap">
            Phone Control Hub
          </h2>
          <p className="text-xs text-[#9AA6BD]">
            Calls · Messages · Installed Apps · Device Toggles
          </p>
        </div>
        <button
          type="button"
          onClick={onSimulateIncomingCall}
          className="min-h-[40px] px-3.5 py-1.5 rounded-xl bg-[#181F2D] hover:bg-[#20293B] border border-white/[0.08] text-xs font-medium text-[#2ECC8F] whitespace-nowrap cursor-pointer transition-colors"
        >
          Simulate Incoming Call
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-5 py-5 space-y-6">
        {/* Quick Device Toggles */}
        <section className="space-y-2.5">
          <h3 className="text-xs font-medium text-[#6B778F]">Device Controls</h3>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
            {(
              [
                {
                  key: 'flashlight',
                  label: deviceState.flashlight ? 'Torch On' : 'Flashlight',
                  active: deviceState.flashlight,
                  icon: Flashlight,
                },
                {
                  key: 'wifi',
                  label: deviceState.wifi ? 'Wi-Fi On' : 'Wi-Fi Off',
                  active: deviceState.wifi,
                  icon: Wifi,
                },
                {
                  key: 'bluetooth',
                  label: deviceState.bluetooth ? 'Bluetooth On' : 'Bluetooth Off',
                  active: deviceState.bluetooth,
                  icon: Bluetooth,
                },
                {
                  key: 'dnd',
                  label: deviceState.dnd ? 'DND On' : 'Do Not Disturb',
                  active: deviceState.dnd,
                  icon: BellOff,
                },
              ] as const
            ).map((item) => {
              const Icon = item.icon;
              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => onToggleSetting(item.key)}
                  className={`min-h-[68px] p-3 rounded-2xl border flex flex-col items-center justify-center gap-1.5 transition-colors cursor-pointer ${
                    item.active
                      ? 'bg-[#4C7DFF]/18 border-[#4C7DFF]/50 text-[#F4F6FA]'
                      : 'bg-[#121722] border-white/[0.08] text-[#9AA6BD] hover:text-[#F4F6FA]'
                  }`}
                >
                  <Icon className="w-5 h-5" />
                  <span className="text-xs font-medium whitespace-nowrap">{item.label}</span>
                </button>
              );
            })}
          </div>

          <div className="rounded-2xl bg-[#121722] border border-white/[0.08] px-4 py-3 flex items-center gap-3">
            <Volume2 className="w-4 h-4 text-[#25C2E3] shrink-0" />
            <span className="text-xs text-[#9AA6BD] whitespace-nowrap">Volume</span>
            <input
              type="range"
              min={0}
              max={100}
              value={deviceState.volume}
              onChange={(e) => onVolumeChange(Number(e.target.value))}
              className="flex-1 accent-[#4C7DFF]"
              aria-label="Device volume"
            />
            <span className="text-xs font-mono tabular-nums text-[#F4F6FA] w-10 text-right">
              {deviceState.volume}%
            </span>
          </div>
        </section>

        {/* Call Control & Dialer + Quick Message */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <section className="rounded-2xl bg-[#121722] border border-white/[0.08] p-4 space-y-3.5">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-medium text-[#F4F6FA]">Call Control</h3>
              <span className="text-xs text-[#9AA6BD] capitalize">
                {deviceState.call.status === 'idle'
                  ? 'Ready'
                  : `${deviceState.call.status} · ${deviceState.call.contactName}`}
              </span>
            </div>

            <div className="grid grid-cols-2 gap-2.5">
              <button
                type="button"
                onClick={() => onManageCall('answer')}
                className="min-h-[44px] rounded-xl bg-[#2ECC8F] hover:bg-[#27B77F] text-white text-xs font-semibold flex items-center justify-center gap-2 cursor-pointer transition-colors"
              >
                <PhoneCall className="w-4 h-4" />
                <span>Answer Call</span>
              </button>
              <button
                type="button"
                onClick={() => onManageCall('decline')}
                className="min-h-[44px] rounded-xl bg-[#F25C69] hover:bg-[#DF4B58] text-white text-xs font-semibold flex items-center justify-center gap-2 cursor-pointer transition-colors"
              >
                <PhoneOff className="w-4 h-4" />
                <span>End / Decline</span>
              </button>
            </div>

            <form onSubmit={handleDialSubmit} className="flex items-center gap-2">
              <input
                type="text"
                value={dialTarget}
                onChange={(e) => setDialTarget(e.target.value)}
                placeholder="Contact name or phone number..."
                className="flex-1 min-h-[44px] px-3.5 rounded-xl bg-[#0A0D14] border border-white/[0.1] text-sm text-[#F4F6FA] placeholder:text-[#6B778F] focus:outline-none focus:border-[#4C7DFF]"
              />
              <button
                type="submit"
                disabled={!dialTarget.trim()}
                aria-label="Place call"
                className="min-h-[44px] px-4 rounded-xl bg-[#4C7DFF] hover:bg-[#3B6CEE] disabled:opacity-40 text-white flex items-center justify-center cursor-pointer"
              >
                <Phone className="w-4 h-4" />
              </button>
            </form>
          </section>

          <section className="rounded-2xl bg-[#121722] border border-white/[0.08] p-4 space-y-3">
            <h3 className="text-sm font-medium text-[#F4F6FA]">Quick Message</h3>
            <input
              type="text"
              value={msgRecipient}
              onChange={(e) => setMsgRecipient(e.target.value)}
              placeholder="Recipient (e.g., Mom, Rahul, +91...)"
              className="w-full min-h-[42px] px-3.5 rounded-xl bg-[#0A0D14] border border-white/[0.1] text-sm text-[#F4F6FA] placeholder:text-[#6B778F] focus:outline-none focus:border-[#4C7DFF]"
            />
            <input
              type="text"
              value={msgBody}
              onChange={(e) => setMsgBody(e.target.value)}
              placeholder="Type your message..."
              className="w-full min-h-[42px] px-3.5 rounded-xl bg-[#0A0D14] border border-white/[0.1] text-sm text-[#F4F6FA] placeholder:text-[#6B778F] focus:outline-none focus:border-[#4C7DFF]"
            />
            <div className="grid grid-cols-2 gap-2.5">
              <button
                type="button"
                disabled={!msgBody.trim()}
                onClick={() => onSendMessage(msgRecipient, msgBody, 'sms')}
                className="min-h-[42px] rounded-xl bg-[#4C7DFF] hover:bg-[#3B6CEE] disabled:opacity-40 text-white text-xs font-medium flex items-center justify-center gap-1.5 cursor-pointer"
              >
                <MessageSquare className="w-3.5 h-3.5" />
                <span>Send SMS</span>
              </button>
              <button
                type="button"
                disabled={!msgBody.trim()}
                onClick={() => onSendMessage(msgRecipient, msgBody, 'whatsapp')}
                className="min-h-[42px] rounded-xl bg-[#181F2D] hover:bg-[#20293B] border border-white/[0.08] disabled:opacity-40 text-[#F4F6FA] text-xs font-medium cursor-pointer"
              >
                Open WhatsApp
              </button>
            </div>
          </section>
        </div>

        {/* App Launcher */}
        <section className="space-y-2.5">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-medium text-[#6B778F]">App Launcher</h3>
            {deviceState.activeApp && (
              <span className="text-xs text-[#25C2E3]">Active: {deviceState.activeApp}</span>
            )}
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5">
            {apps.map((app) => {
              const active = deviceState.activeApp?.toLowerCase() === app.name.toLowerCase();
              return (
                <button
                  key={app.id}
                  type="button"
                  onClick={() => onOpenApp(app.name)}
                  className={`min-h-[54px] px-3.5 py-2.5 rounded-2xl border flex items-center justify-between gap-2 text-left transition-colors cursor-pointer ${
                    active
                      ? 'bg-[#4C7DFF]/18 border-[#4C7DFF]/50 text-[#F4F6FA]'
                      : 'bg-[#121722] border-white/[0.08] text-[#F4F6FA] hover:bg-[#181F2D]'
                  }`}
                >
                  <div className="min-w-0">
                    <p className="text-sm font-medium truncate">{app.name}</p>
                    <p className="text-[11px] text-[#9AA6BD] truncate">{app.category}</p>
                  </div>
                  <ExternalLink className="w-3.5 h-3.5 text-[#25C2E3] shrink-0" />
                </button>
              );
            })}
          </div>
        </section>

        {/* Saved Contacts */}
        <section className="rounded-2xl bg-[#121722] border border-white/[0.08] p-4 space-y-3">
          <h3 className="text-sm font-medium text-[#F4F6FA]">Contacts Directory</h3>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
            {contacts.map((c) => (
              <div
                key={c.id}
                className="rounded-xl bg-[#0A0D14] border border-white/[0.06] px-3 py-2.5 flex items-center justify-between gap-2"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium text-[#F4F6FA] truncate">{c.name}</p>
                  <p className="text-xs text-[#9AA6BD] tabular-nums truncate">{c.phone}</p>
                </div>
                <button
                  type="button"
                  onClick={() => onDialNumber(c.name)}
                  aria-label={`Call ${c.name}`}
                  className="min-h-[36px] min-w-[36px] rounded-lg bg-[#4C7DFF]/15 text-[#25C2E3] flex items-center justify-center cursor-pointer"
                >
                  <Phone className="w-3.5 h-3.5" />
                </button>
              </div>
            ))}
          </div>
          <form onSubmit={handleAddContact} className="flex flex-wrap items-center gap-2 pt-1">
            <input
              type="text"
              value={newContactName}
              onChange={(e) => setNewContactName(e.target.value)}
              placeholder="Contact name"
              className="flex-1 min-w-[130px] min-h-[40px] px-3 rounded-xl bg-[#0A0D14] border border-white/[0.08] text-xs text-[#F4F6FA]"
            />
            <input
              type="text"
              value={newContactPhone}
              onChange={(e) => setNewContactPhone(e.target.value)}
              placeholder="Phone number"
              className="flex-1 min-w-[140px] min-h-[40px] px-3 rounded-xl bg-[#0A0D14] border border-white/[0.08] text-xs text-[#F4F6FA]"
            />
            <button
              type="submit"
              disabled={!newContactName.trim() || !newContactPhone.trim()}
              className="min-h-[40px] px-3.5 rounded-xl bg-[#181F2D] border border-white/[0.08] text-xs font-medium text-[#F4F6FA] flex items-center gap-1 cursor-pointer disabled:opacity-40"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add</span>
            </button>
          </form>
        </section>

        {/* Recent Actions Log */}
        {actionLogs.length > 0 && (
          <section className="space-y-2.5">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-medium text-[#6B778F]">Recent Phone Actions</h3>
              <button
                type="button"
                onClick={onClearLogs}
                className="text-xs text-[#9AA6BD] hover:text-[#F4F6FA] cursor-pointer"
              >
                Clear
              </button>
            </div>
            <div className="space-y-2">
              {actionLogs.slice(0, 8).map((log) => (
                <div
                  key={log.id}
                  className="rounded-xl bg-[#121722] border border-white/[0.08] px-4 py-2.5 flex items-center justify-between gap-3"
                >
                  <div className="min-w-0">
                    <p className="text-sm text-[#F4F6FA] truncate">{log.title}</p>
                    <p className="text-xs text-[#9AA6BD] truncate">{log.subtitle}</p>
                  </div>
                  <span className="text-xs font-mono tabular-nums text-[#6B778F] whitespace-nowrap">
                    {new Date(log.timestamp).toLocaleTimeString([], {
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>
                </div>
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  );
}

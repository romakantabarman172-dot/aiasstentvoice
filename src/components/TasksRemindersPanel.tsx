import { useState } from 'react';
import { AlarmClock, Check, Plus, Trash2, X } from 'lucide-react';
import type { ReminderItem, TaskItem } from '../types';

interface TasksRemindersPanelProps {
  tasks: TaskItem[];
  reminders: ReminderItem[];
  onAddTask: (title: string, category: string, priority: 'High' | 'Normal' | 'Low', dueLabel: string) => void;
  onToggleTask: (id: string) => void;
  onDeleteTask: (id: string) => void;
  onAddReminder: (title: string, delayMinutes: number) => void;
  onDismissReminder: (id: string) => void;
}

type AgendaFilter = 'all' | 'tasks' | 'reminders';

export function TasksRemindersPanel({
  tasks,
  reminders,
  onAddTask,
  onToggleTask,
  onDeleteTask,
  onAddReminder,
  onDismissReminder,
}: TasksRemindersPanelProps) {
  const [filter, setFilter] = useState<AgendaFilter>('all');
  const [modalOpen, setModalOpen] = useState(false);
  const [mode, setMode] = useState<'task' | 'reminder'>('task');
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState('Personal');
  const [priority, setPriority] = useState<'High' | 'Normal' | 'Low'>('Normal');
  const [delayMinutes, setDelayMinutes] = useState(15);

  const visibleReminders = reminders.filter((r) => !r.isDismissed);
  const pendingCount = tasks.filter((t) => !t.isCompleted).length;

  const showReminders = filter === 'all' || filter === 'reminders';
  const showTasks = filter === 'all' || filter === 'tasks';

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    if (mode === 'task') {
      onAddTask(title.trim(), category, priority, 'Today');
    } else {
      onAddReminder(title.trim(), delayMinutes);
    }
    setTitle('');
    setModalOpen(false);
  };

  return (
    <div className="flex flex-col h-full min-h-0">
      {/* Header */}
      <div className="flex items-center justify-between gap-4 px-5 py-3.5 border-b border-white/[0.08] shrink-0">
        <div className="min-w-0">
          <h2 className="text-lg font-semibold tracking-tight text-[#F4F6FA] whitespace-nowrap">
            Tasks & Reminders
          </h2>
          <p className="text-xs text-[#9AA6BD] tabular-nums">
            {pendingCount} pending · {visibleReminders.length} reminders
          </p>
        </div>

        <button
          type="button"
          onClick={() => setModalOpen(true)}
          className="min-h-[42px] px-4 py-2 rounded-xl bg-[#4C7DFF] hover:bg-[#3B6CEE] text-white text-xs font-medium flex items-center gap-1.5 whitespace-nowrap cursor-pointer transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>Add Item</span>
        </button>
      </div>

      {/* Segmented Filter Tabs */}
      <div className="px-5 pt-4 pb-2 shrink-0">
        <div className="inline-flex items-center gap-1 p-1 rounded-xl bg-[#121722] border border-white/[0.08]">
          {(
            [
              { id: 'all', label: 'All' },
              { id: 'tasks', label: 'Tasks' },
              { id: 'reminders', label: 'Reminders' },
            ] as const
          ).map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setFilter(tab.id)}
              className={`min-h-[36px] px-4 py-1.5 rounded-lg text-xs font-medium transition-colors whitespace-nowrap cursor-pointer ${
                filter === tab.id
                  ? 'bg-[#181F2D] text-[#F4F6FA]'
                  : 'text-[#9AA6BD] hover:text-[#F4F6FA]'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* List Viewport */}
      <div className="flex-1 overflow-y-auto px-5 py-4 space-y-6">
        {(!showReminders || visibleReminders.length === 0) &&
        (!showTasks || tasks.length === 0) ? (
          <div className="py-16 text-center max-w-sm mx-auto">
            <p className="text-base font-medium text-[#F4F6FA]">Nothing scheduled yet</p>
            <p className="mt-1.5 text-sm text-[#9AA6BD]">
              Add an item manually or tell Aura in English or Hinglish to create tasks and reminders.
            </p>
            <button
              type="button"
              onClick={() => setModalOpen(true)}
              className="mt-5 min-h-[42px] px-4 py-2 rounded-xl bg-[#181F2D] border border-white/[0.08] text-sm font-medium text-[#F4F6FA] hover:bg-[#1E2738] transition-colors cursor-pointer"
            >
              Create First Entry
            </button>
          </div>
        ) : (
          <>
            {showReminders && visibleReminders.length > 0 && (
              <section className="space-y-2.5">
                <h3 className="text-xs font-medium text-[#6B778F]">Reminders</h3>
                <div className="space-y-2">
                  {visibleReminders.map((reminder) => (
                    <div
                      key={reminder.id}
                      className="rounded-2xl bg-[#121722] border border-white/[0.08] px-4 py-3 flex items-center justify-between gap-3"
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <div className="w-9 h-9 rounded-full bg-[#F5A524]/15 text-[#F5A524] flex items-center justify-center shrink-0">
                          <AlarmClock className="w-4 h-4" />
                        </div>
                        <div className="min-w-0">
                          <p className="text-sm font-medium text-[#F4F6FA] truncate">
                            {reminder.title}
                          </p>
                          <p className="text-xs text-[#9AA6BD] tabular-nums">
                            {reminder.isFired ? 'Triggered' : 'Scheduled'} · {reminder.timeLabel}
                          </p>
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => onDismissReminder(reminder.id)}
                        aria-label="Dismiss reminder"
                        className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg text-[#6B778F] hover:text-[#F25C69] transition-colors cursor-pointer"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  ))}
                </div>
              </section>
            )}

            {showTasks && tasks.length > 0 && (
              <section className="space-y-2.5">
                <h3 className="text-xs font-medium text-[#6B778F]">Daily Tasks</h3>
                <div className="space-y-2">
                  {tasks.map((task) => (
                    <div
                      key={task.id}
                      className="rounded-2xl bg-[#121722] border border-white/[0.08] px-3 py-2.5 flex items-center justify-between gap-3"
                    >
                      <button
                        type="button"
                        onClick={() => onToggleTask(task.id)}
                        className="flex items-center gap-3 flex-1 min-w-0 text-left min-h-[44px] cursor-pointer"
                      >
                        <span
                          className={`w-6 h-6 rounded-full border flex items-center justify-center shrink-0 transition-colors ${
                            task.isCompleted
                              ? 'bg-[#2ECC8F] border-[#2ECC8F] text-white'
                              : 'border-[#6B778F] text-transparent'
                          }`}
                        >
                          <Check className="w-3.5 h-3.5" />
                        </span>
                        <div className="min-w-0">
                          <p
                            className={`text-sm font-medium truncate ${
                              task.isCompleted ? 'line-through text-[#6B778F]' : 'text-[#F4F6FA]'
                            }`}
                          >
                            {task.title}
                          </p>
                          <p className="text-xs text-[#9AA6BD]">
                            {task.category} · {task.dueLabel} · {task.priority}
                          </p>
                        </div>
                      </button>

                      <button
                        type="button"
                        onClick={() => onDeleteTask(task.id)}
                        aria-label="Delete task"
                        className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg text-[#6B778F] hover:text-[#F25C69] transition-colors cursor-pointer"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  ))}
                </div>
              </section>
            )}
          </>
        )}
      </div>

      {/* Add Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-end sm:items-center justify-center p-4">
          <form
            onSubmit={handleSave}
            className="w-full max-w-md rounded-2xl bg-[#121722] border border-white/[0.1] p-5 space-y-4"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-base font-semibold text-[#F4F6FA]">
                {mode === 'task' ? 'New Daily Task' : 'New Reminder'}
              </h3>
              <button
                type="button"
                onClick={() => setModalOpen(false)}
                aria-label="Close modal"
                className="min-h-[40px] min-w-[40px] flex items-center justify-center text-[#9AA6BD] hover:text-[#F4F6FA] cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="grid grid-cols-2 gap-1 p-1 rounded-xl bg-[#0A0D14]">
              {(['task', 'reminder'] as const).map((m) => (
                <button
                  key={m}
                  type="button"
                  onClick={() => setMode(m)}
                  className={`min-h-[38px] rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                    mode === m ? 'bg-[#4C7DFF] text-white' : 'text-[#9AA6BD]'
                  }`}
                >
                  {m === 'task' ? 'Task' : 'Reminder'}
                </button>
              ))}
            </div>

            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={mode === 'task' ? 'Task title...' : 'Remind me to...'}
              autoFocus
              className="w-full min-h-[44px] px-3.5 rounded-xl bg-[#0A0D14] border border-white/[0.1] text-sm text-[#F4F6FA] placeholder:text-[#6B778F] focus:outline-none focus:border-[#4C7DFF]"
            />

            {mode === 'reminder' ? (
              <div className="space-y-2">
                <label className="text-xs text-[#9AA6BD] block">Trigger after</label>
                <div className="grid grid-cols-4 gap-2">
                  {[
                    { m: 1, label: '1m' },
                    { m: 5, label: '5m' },
                    { m: 15, label: '15m' },
                    { m: 60, label: '1h' },
                  ].map((opt) => (
                    <button
                      key={opt.m}
                      type="button"
                      onClick={() => setDelayMinutes(opt.m)}
                      className={`min-h-[40px] rounded-lg text-xs font-medium border cursor-pointer ${
                        delayMinutes === opt.m
                          ? 'bg-[#4C7DFF] border-[#4C7DFF] text-white'
                          : 'bg-[#181F2D] border-white/[0.08] text-[#F4F6FA]'
                      }`}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-[#9AA6BD] block mb-1.5">Priority</label>
                  <select
                    value={priority}
                    onChange={(e) => setPriority(e.target.value as 'High' | 'Normal' | 'Low')}
                    className="w-full min-h-[42px] px-3 rounded-xl bg-[#0A0D14] border border-white/[0.1] text-sm text-[#F4F6FA]"
                  >
                    <option value="High">High</option>
                    <option value="Normal">Normal</option>
                    <option value="Low">Low</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs text-[#9AA6BD] block mb-1.5">Category</label>
                  <select
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                    className="w-full min-h-[42px] px-3 rounded-xl bg-[#0A0D14] border border-white/[0.1] text-sm text-[#F4F6FA]"
                  >
                    <option value="Personal">Personal</option>
                    <option value="Work">Work</option>
                    <option value="Health">Health</option>
                    <option value="Shopping">Shopping</option>
                  </select>
                </div>
              </div>
            )}

            <button
              type="submit"
              disabled={!title.trim()}
              className="w-full min-h-[46px] rounded-xl bg-[#4C7DFF] hover:bg-[#3B6CEE] disabled:opacity-40 text-sm font-medium text-white cursor-pointer transition-colors"
            >
              {mode === 'task' ? 'Save Task' : 'Schedule Reminder'}
            </button>
          </form>
        </div>
      )}
    </div>
  );
}

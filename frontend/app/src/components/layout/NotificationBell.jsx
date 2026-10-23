import React, { useCallback, useEffect, useState } from 'react';
import { Bell } from 'lucide-react';
import api from '../../services/api';

const POLL_MS = 60 * 1000;

/**
 * Bell icon with the signed-in user's real notifications (GET /api/notifications).
 * Polls once a minute; shows an unread badge and lets the user mark items as read.
 */
const NotificationBell = () => {
    const [open, setOpen] = useState(false);
    const [items, setItems] = useState([]);
    const [unread, setUnread] = useState(0);
    const [error, setError] = useState(false);

    const load = useCallback(async () => {
        try {
            const data = await api.notifications.getMine();
            setItems(Array.isArray(data?.items) ? data.items : []);
            setUnread(Number(data?.unread) || 0);
            setError(false);
        } catch (e) {
            setError(true);
        }
    }, []);

    useEffect(() => {
        load();
        const timer = setInterval(load, POLL_MS);
        return () => clearInterval(timer);
    }, [load]);

    const markRead = async (id) => {
        try {
            await api.notifications.markRead(id);
            setItems((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
            setUnread((u) => Math.max(0, u - 1));
        } catch (e) {
            setError(true);
        }
    };

    const markAllRead = async () => {
        try {
            await api.notifications.markAllRead();
            setItems((prev) => prev.map((n) => ({ ...n, read: true })));
            setUnread(0);
        } catch (e) {
            setError(true);
        }
    };

    return (
        <div className="relative">
            <button
                type="button"
                aria-label={unread > 0 ? `Notifications (${unread} unread)` : 'Notifications'}
                onClick={() => setOpen((v) => !v)}
                className="relative p-2 text-gray-500 dark:text-slate-400 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
            >
                <Bell className="w-5 h-5" />
                {unread > 0 && (
                    <span
                        data-testid="notification-badge"
                        className="absolute -top-0.5 -right-0.5 min-w-[1.1rem] h-[1.1rem] px-1 rounded-full bg-red-500 text-white text-[10px] leading-[1.1rem] text-center"
                    >
                        {unread > 9 ? '9+' : unread}
                    </span>
                )}
            </button>

            {open && (
                <div className="absolute right-0 mt-2 w-80 bg-white dark:bg-slate-800 rounded-xl shadow-xl border border-gray-100 dark:border-slate-700 py-2 z-50">
                    <div className="px-4 py-3 border-b border-gray-100 dark:border-slate-700 flex justify-between items-center">
                        <h3 className="font-semibold text-gray-900 dark:text-slate-100">Notifications</h3>
                        <div className="flex gap-3">
                            {unread > 0 && (
                                <button type="button" className="text-xs font-medium text-blue-600 dark:text-blue-400" onClick={markAllRead}>
                                    Mark all read
                                </button>
                            )}
                            <button type="button" className="text-xs font-medium text-gray-500" onClick={() => setOpen(false)}>
                                Close
                            </button>
                        </div>
                    </div>

                    {error && (
                        <div role="alert" className="px-4 py-3 text-xs text-red-600">
                            Could not load notifications.
                        </div>
                    )}

                    {!error && items.length === 0 && (
                        <div className="px-4 py-6 text-center text-sm text-gray-500 dark:text-slate-400">
                            No notifications
                        </div>
                    )}

                    <ul className="max-h-80 overflow-y-auto divide-y divide-gray-100 dark:divide-slate-700">
                        {items.map((n) => (
                            <li key={n.id}>
                                <button
                                    type="button"
                                    onClick={() => !n.read && markRead(n.id)}
                                    className={`w-full text-left px-4 py-3 text-sm hover:bg-gray-50 dark:hover:bg-slate-700/50 ${n.read ? 'opacity-60' : ''}`}
                                >
                                    <span className="block font-medium text-gray-900 dark:text-slate-100">{n.title}</span>
                                    {n.message && <span className="block text-xs text-gray-600 dark:text-slate-300">{n.message}</span>}
                                    <span className="block text-[11px] text-gray-400 mt-1">
                                        {n.createdAt ? new Date(n.createdAt).toLocaleString() : ''}
                                    </span>
                                </button>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    );
};

export default NotificationBell;

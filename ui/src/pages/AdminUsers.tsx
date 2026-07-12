import { useState, useEffect } from 'react';
import { adminApi } from '@/api/client';
import type { User } from '@/types';
import { Navbar } from '@/components/Navbar';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { getInitials, formatRelativeDate } from '@/lib/utils';
import { MoreVertical, Download, Plus, ChevronLeft, ChevronRight, Badge as BadgeIcon, History, ShieldCheck } from 'lucide-react';

export default function AdminUsers() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);

  useEffect(() => {
    loadUsers();
  }, [page]);

  const loadUsers = async () => {
    try {
      const res = await adminApi.listUsers(page);
      setUsers(res.data.content || []);
      setTotal(res.data.totalElements);
    } catch (err) {
      console.error('Failed to load users:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleRoleChange = async (userId: string, role: 'ADMIN' | 'RECRUITER') => {
    try {
      await adminApi.updateRole(userId, role);
      loadUsers();
    } catch (err) {
      console.error('Failed to update role:', err);
    }
  };

  const pageSize = 10;
  const totalPages = Math.ceil(total / pageSize);

  return (
    <div className="min-h-screen bg-surface">
      <Navbar />
      <main className="max-w-6xl mx-auto px-8 py-12">
        <div className="flex items-end justify-between mb-8">
          <div>
            <h1 className="text-4xl font-semibold tracking-tight">User Management</h1>
            <div className="flex items-center gap-2 pt-2">
              <Badge variant="primary">{total} Users Total</Badge>
              <span className="text-sm text-on-surface-variant">
                • {page * pageSize + 1} - {Math.min((page + 1) * pageSize, total)} shown
              </span>
            </div>
          </div>
          <div className="flex gap-3">
            <Button variant="outline">
              <Download className="w-4 h-4" />
              Export CSV
            </Button>
            <Button>
              <Plus className="w-4 h-4" />
              New User
            </Button>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm overflow-hidden border border-outline-variant/10">
          {loading ? (
            <div className="flex items-center justify-center py-24">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
            </div>
          ) : (
            <table className="w-full text-left">
              <thead className="bg-surface-container-low text-on-surface-variant">
                <tr>
                  <th className="px-8 py-5 text-xs font-semibold uppercase">Name</th>
                  <th className="px-8 py-5 text-xs font-semibold uppercase">Email</th>
                  <th className="px-8 py-5 text-xs font-semibold uppercase">Role</th>
                  <th className="px-8 py-5 text-xs font-semibold uppercase">Joined</th>
                  <th className="px-8 py-5 text-xs font-semibold uppercase text-right">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-container/50">
                {users.map((user) => (
                  <tr
                    key={user.id}
                    className="hover:bg-surface-container-low/30 transition-colors"
                  >
                    <td className="px-8 py-5">
                      <div className="flex items-center gap-4">
                        <div className="w-10 h-10 rounded-full bg-primary-container flex items-center justify-center text-primary font-bold text-xs">
                          {getInitials(user.fullName)}
                        </div>
                        <span className="font-semibold">{user.fullName}</span>
                      </div>
                    </td>
                    <td className="px-8 py-5 text-on-surface-variant font-medium">
                      {user.email}
                    </td>
                    <td className="px-8 py-5">
                      <Badge
                        variant={user.role === 'ADMIN' ? 'secondary' : 'default'}
                      >
                        {user.role}
                      </Badge>
                    </td>
                    <td className="px-8 py-5 text-on-surface-variant text-sm">
                      {formatRelativeDate(user.createdAt)}
                    </td>
                    <td className="px-8 py-5 text-right">
                      <div className="relative inline-block">
                        <Button variant="ghost" size="icon">
                          <MoreVertical className="w-4 h-4" />
                        </Button>
                        <div className="absolute right-0 mt-2 w-48 bg-white rounded-2xl shadow-xl border border-outline-variant/10 py-2 z-10 hidden group-hover:block">
                          {user.role === 'ADMIN' ? (
                            <button
                              onClick={() =>
                                handleRoleChange(user.id, 'RECRUITER')
                              }
                              className="w-full px-4 py-2 text-sm text-left hover:bg-surface-container-low flex items-center gap-2"
                            >
                              Set as Recruiter
                            </button>
                          ) : (
                            <button
                              onClick={() =>
                                handleRoleChange(user.id, 'ADMIN')
                              }
                              className="w-full px-4 py-2 text-sm text-left hover:bg-surface-container-low flex items-center gap-2"
                            >
                              Set as Admin
                            </button>
                          )}
                        </div>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <div className="px-8 py-5 bg-surface-container-low/20 flex items-center justify-between border-t border-surface-container/50">
            <span className="text-sm text-on-surface-variant">
              Showing{' '}
              <span className="font-bold text-on-surface">
                {page * pageSize + 1} - {Math.min((page + 1) * pageSize, total)}
              </span>
              {' '}of {total} users
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                size="icon"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                <ChevronLeft className="w-4 h-4" />
              </Button>
              {Array.from({ length: Math.min(5, totalPages) }, (_, i) => (
                <Button
                  key={i}
                  variant={page === i ? 'default' : 'ghost'}
                  size="icon"
                  onClick={() => setPage(i)}
                >
                  {i + 1}
                </Button>
              ))}
              <Button
                variant="ghost"
                size="icon"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                <ChevronRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-8">
          <div className="bg-surface-container-low/40 rounded-xl p-6 flex items-center gap-4 border border-outline-variant/10">
            <div className="w-12 h-12 rounded-2xl bg-white shadow-sm flex items-center justify-center text-primary">
              <BadgeIcon className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">
                Active Roles
              </p>
              <h4 className="text-2xl font-bold">14 Core Roles</h4>
            </div>
          </div>
          <div className="bg-surface-container-low/40 rounded-xl p-6 flex items-center gap-4 border border-outline-variant/10">
            <div className="w-12 h-12 rounded-2xl bg-white shadow-sm flex items-center justify-center text-secondary">
              <History className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">
                Avg. Tenure
              </p>
              <h4 className="text-2xl font-bold">1.4 Years</h4>
            </div>
          </div>
          <div className="bg-surface-container-low/40 rounded-xl p-6 flex items-center gap-4 border border-outline-variant/10">
            <div className="w-12 h-12 rounded-2xl bg-white shadow-sm flex items-center justify-center text-tertiary">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <div>
              <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">
                Compliance
              </p>
              <h4 className="text-2xl font-bold">99.8% Pass</h4>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
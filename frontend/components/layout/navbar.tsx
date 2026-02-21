'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Database, GitBranch } from 'lucide-react';

const navItems = [
  { href: '/', label: 'Workflows', icon: GitBranch },
  { href: '/data-marts', label: 'Data Marts', icon: Database },
];

export function Navbar() {
  const pathname = usePathname();

  return (
    <header className="border-b bg-white">
      <div className="flex h-14 items-center px-6">
        <Link href="/" className="text-lg font-semibold mr-8">
          Segment Builder
        </Link>
        <nav className="flex gap-1">
          {navItems.map((item) => {
            const isActive = item.href === '/'
              ? pathname === '/'
              : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-gray-100 text-gray-900'
                    : 'text-gray-500 hover:text-gray-900 hover:bg-gray-50'
                }`}
              >
                <item.icon className="h-4 w-4" />
                {item.label}
              </Link>
            );
          })}
        </nav>
      </div>
    </header>
  );
}

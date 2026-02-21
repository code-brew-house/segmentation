'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import type { DataMart } from '@/lib/types';
import { DataMartCard } from '@/components/data-marts/data-mart-card';
import { Input } from '@/components/ui/input';
import { Search } from 'lucide-react';

export default function DataMartsPage() {
  const [dataMarts, setDataMarts] = useState<DataMart[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.listDataMarts()
      .then(setDataMarts)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const filtered = dataMarts.filter((dm) =>
    dm.tableName.toLowerCase().includes(search.toLowerCase()) ||
    dm.description.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Data Marts</h1>
        <div className="relative w-64">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
          <Input
            placeholder="Search tables..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
      </div>

      {loading ? (
        <p className="text-gray-500">Loading data marts...</p>
      ) : filtered.length === 0 ? (
        <p className="text-gray-500">No data marts found</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map((dm) => (
            <DataMartCard key={dm.id} dataMart={dm} />
          ))}
        </div>
      )}
    </div>
  );
}

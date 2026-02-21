'use client';

import { useState, useEffect, use } from 'react';
import { api } from '@/lib/api';
import type { DataMartDetail } from '@/lib/types';
import { ColumnTable } from '@/components/data-marts/column-table';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Database } from 'lucide-react';
import Link from 'next/link';

export default function DataMartDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const [dataMart, setDataMart] = useState<DataMartDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.getDataMart(id)
      .then(setDataMart)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <div className="p-6 text-gray-500">Loading...</div>;
  if (!dataMart) return <div className="p-6 text-red-500">Data mart not found</div>;

  return (
    <div className="p-6">
      <div className="mb-6">
        <Link href="/data-marts">
          <Button variant="ghost" size="sm">
            <ArrowLeft className="h-4 w-4 mr-1" />
            Back to Data Marts
          </Button>
        </Link>
      </div>

      <div className="flex items-center gap-3 mb-2">
        <Database className="h-6 w-6 text-blue-500" />
        <h1 className="text-2xl font-bold">{dataMart.tableName}</h1>
      </div>
      <p className="text-gray-500 mb-1">{dataMart.description}</p>
      <p className="text-sm text-gray-400 mb-6">Schema: {dataMart.schemaName}</p>

      <div className="bg-white rounded-lg border">
        <div className="p-4 border-b">
          <h2 className="font-semibold">Columns ({dataMart.columns.length})</h2>
        </div>
        <ColumnTable columns={dataMart.columns} />
      </div>
    </div>
  );
}

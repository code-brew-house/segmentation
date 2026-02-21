'use client';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Database } from 'lucide-react';
import type { DataMart } from '@/lib/types';
import Link from 'next/link';

export function DataMartCard({ dataMart }: { dataMart: DataMart }) {
  return (
    <Link href={`/data-marts/${dataMart.id}`}>
      <Card className="hover:shadow-md transition-shadow cursor-pointer">
        <CardHeader className="flex flex-row items-center gap-3 pb-2">
          <Database className="h-5 w-5 text-blue-500" />
          <CardTitle className="text-base font-medium">{dataMart.tableName}</CardTitle>
          <Badge variant="secondary" className="ml-auto">{dataMart.columnCount} columns</Badge>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-gray-500">{dataMart.description}</p>
          <p className="text-xs text-gray-400 mt-1">Schema: {dataMart.schemaName}</p>
        </CardContent>
      </Card>
    </Link>
  );
}

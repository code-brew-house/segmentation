'use client';

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import type { DataMartColumn } from '@/lib/types';

export function ColumnTable({ columns }: { columns: DataMartColumn[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-8">#</TableHead>
          <TableHead>Column Name</TableHead>
          <TableHead>Data Type</TableHead>
          <TableHead>Description</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {columns.map((col) => (
          <TableRow key={col.id}>
            <TableCell className="text-gray-400">{col.ordinalPosition}</TableCell>
            <TableCell className="font-mono text-sm">{col.columnName}</TableCell>
            <TableCell>
              <Badge variant="outline" className="font-mono text-xs">{col.dataType}</Badge>
            </TableCell>
            <TableCell className="text-gray-500">{col.description}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

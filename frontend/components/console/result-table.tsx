'use client';

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';

interface ResultTableProps {
  rows: Record<string, unknown>[];
}

export function ResultTable({ rows }: ResultTableProps) {
  if (rows.length === 0) {
    return <p className="text-sm text-gray-500 p-4">No result data to display.</p>;
  }

  const MAX_DISPLAY_ROWS = 100;
  const columns = Object.keys(rows[0]);
  const displayRows = rows.slice(0, MAX_DISPLAY_ROWS);
  const truncated = rows.length > MAX_DISPLAY_ROWS;

  return (
    <div className="overflow-auto max-h-[300px]">
      <Table>
        <TableHeader>
          <TableRow>
            {columns.map((col) => (
              <TableHead key={col}>{col}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {displayRows.map((row, i) => (
            <TableRow key={i}>
              {columns.map((col) => (
                <TableCell key={col}>
                  {row[col] == null ? '' : String(row[col])}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {truncated && (
        <p className="text-xs text-gray-400 px-4 py-2">
          Showing {MAX_DISPLAY_ROWS} of {rows.length} rows.
        </p>
      )}
    </div>
  );
}

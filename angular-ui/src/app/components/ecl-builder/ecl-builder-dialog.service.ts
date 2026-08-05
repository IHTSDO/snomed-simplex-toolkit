import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { EclBuilderDialogComponent, EclBuilderDialogData } from './ecl-builder-dialog/ecl-builder-dialog.component';

@Injectable({
  providedIn: 'root'
})
export class EclBuilderDialogService {
  constructor(private dialog: MatDialog) {}

  open(initialEcl = '', branchPath = 'MAIN'): Observable<string | null> {
    const data: EclBuilderDialogData = { ecl: initialEcl, branchPath };
    return this.dialog.open(EclBuilderDialogComponent, {
      data,
      width: '80%',
      height: '80%'
    }).afterClosed().pipe(
      map((result: string | undefined) => result ?? null)
    );
  }
}

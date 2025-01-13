import { Component, EventEmitter, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-create-shared-set',
  templateUrl: './create-shared-set.component.html',
  styleUrls: ['./create-shared-set.component.scss']
})
export class CreateSharedSetComponent {
  @Output() close = new EventEmitter<void>();
  sharedSetForm: FormGroup;

  constructor(private fb: FormBuilder, private simplexService: SimplexService, private snackBar: MatSnackBar) {
    this.sharedSetForm = this.fb.group({
      name: ['', Validators.required],
      slug: ['', Validators.required],
      ecl: ['', Validators.required],
    });
  }

  onSubmit(): void {
    if (this.sharedSetForm.valid) {
      const { name, slug, ecl } = this.sharedSetForm.value;
      const project = 'shared';

      // Call the service to create a shared set
      this.simplexService.createSharedSet({ name, slug, project }).subscribe({
        next: (response) => {
          this.snackBar.open('Shared set created successfully', 'Dismiss');
          this.sharedSetForm.reset();
          this.sharedSetForm.markAsPristine();
          this.closePanel();
        },
        error: (err) => {
          this.snackBar.open('Error creating shared set', 'Dismiss');
        },
      });
    }
  }

  closePanel() {
    this.close.emit();
  }
}

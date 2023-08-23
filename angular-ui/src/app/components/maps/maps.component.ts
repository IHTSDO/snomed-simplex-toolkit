import { Component, Input, SimpleChanges } from '@angular/core';
import { FormGroup, Validators, FormBuilder } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { lastValueFrom } from 'rxjs';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-maps',
  templateUrl: './maps.component.html',
  styleUrls: ['./maps.component.scss']
})
export class MapsComponent {
  @Input() edition: string;
  maps = [];
  selectedmap = null;
  newmapMode = false;
  loading = false;
  mapFields = ["idAndFsnTerm", "active", "activeMemberCount", "moduleId"];
  saving = false;

  form: FormGroup = this.fb.group({
    preferredTerm: ['', Validators.required]
  });

  constructor(private fb: FormBuilder,
              private simplexService: SimplexService,
              private snackBar: MatSnackBar) {}

  ngOnChanges(changes: SimpleChanges) {
    if (changes['edition'] && changes['edition'].currentValue) {
      this.loadmaps(changes['edition'].currentValue);
    }
  }

  loadmaps(edition: string) {
    this.maps = [];
    this.loading = true;
    this.simplexService.getSimpleMaps(edition).subscribe((maps) => {
      this.maps = maps;
      this.loading = false;
    });
  }

  get formKeys(): string[] {
    return Object.keys(this.form.controls);
  }

  onClick(item: any) {
    this.selectedmap = item;
  }
  submit() {
    this.form.markAllAsTouched();
    if (this.form.valid) {
      const map = {
        preferredTerm: this.form.value.preferredTerm
      };
      this.saving = true;
      // Set the form to disabled
      this.form.disable();
      lastValueFrom(this.simplexService.createMap(this.edition, map)).then(
        (edition) => {
          this.saving = false;
          this.form.reset();
          this.newmapMode = false;
          this.loadmaps(this.edition);
        },
        (error) => {
          console.error(error);
          this.saving = false;
          this.snackBar.open('Failed to create map', 'Dismiss', {
            duration: 5000
          });
        }
      );
    }
  }
}

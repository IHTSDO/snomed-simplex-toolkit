import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

@Component({
  selector: 'app-product-packaging',
  templateUrl: './product-packaging.component.html',
  styleUrls: ['./product-packaging.component.scss']
})
export class ProductPackagingComponent implements OnInit, OnChanges {

  @Input() edition: any;
  packagingForm: FormGroup;
  formKeys: string[] = ['orgName', 'orgContactDetails'];
  saving = false;
  loading = false;
  private lastChangeTime = 0;

  constructor(private fb: FormBuilder, private simplexService: SimplexService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['edition'] && changes['edition'].currentValue) {
      const currentTime = Date.now();
      if (currentTime - this.lastChangeTime < 2000) {
        return;
      }
      this.lastChangeTime = currentTime;
      this.packagingForm?.reset();
      this.getProductPackagingConfiguration();
    }
  }

  ngOnInit(): void {
    this.initializeForm();
    this.getProductPackagingConfiguration();
  }

  initializeForm(): void {
    this.packagingForm = this.fb.group({
      orgName: ['', Validators.required],
      orgContactDetails: ['', [Validators.required, Validators.email]]
    });
  }

  getProductPackagingConfiguration(): void {
    this.loading = true;
    this.simplexService.getProductPackagingConfiguration(this.edition.shortName).subscribe(
      (data) => {
        this.packagingForm.patchValue({
          orgName: data.orgName || '',
          orgContactDetails: data.orgContactDetails || ''
        });
        this.loading = false;
      },
      (error) => {
        console.error('Error fetching packaging configuration', error);
        this.loading = false;
      }
    );
  }

  submit(): void {
    if (this.packagingForm.valid) {
      this.saving = true;
      this.simplexService.updateProductPackagingConfiguration(this.edition.shortName, this.packagingForm.value).subscribe(
        (response) => {
          console.log('Packaging details saved successfully', response);
          this.saving = false;
        },
        (error) => {
          console.error('Error saving packaging details', error);
          this.saving = false;
        }
      );
    }
  }

  closePanelEvent(): void {
    // Logic to close the panel (customize as needed)
    console.log('Panel closed');
  }
}

import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-new-edition',
  templateUrl: './new-edition.component.html',
  styleUrls: ['./new-edition.component.scss']
})
export class NewEditionComponent {

  @Output() closePanel = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    name: ['', Validators.required],
    description: [''],
    url: [''],
    version: [''],
    status: [''],
    content: ['']
  });

  constructor(private fb: FormBuilder) { }

  submit() {
    if (this.form.valid) {
      const edition = {
        resourceType: 'CodeSystem',
        name: this.form.value.name,
        description: this.form.value.description,
        url: this.form.value.url,
        version: this.form.value.version,
        status: this.form.value.status,
        content: this.form.value.content
      };
      console.log(edition);
      // Save Edition
      this.closePanelEvent();
    }
  }

  closePanelEvent() {
    this.closePanel.emit();
  }

}
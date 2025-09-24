import { Component, ElementRef, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormControl } from '@angular/forms';
import {debounceTime, distinctUntilChanged, finalize, map, startWith, switchMap,tap} from 'rxjs/operators';
import {concat, Observable, of, Subject} from 'rxjs';
import { TerminologyService } from '../../../services/simplex/terminology.service';
import { MatFormFieldControl } from '@angular/material/form-field';

/**
 * Interface for the binding object passed to the autocomplete binding component
 */
interface AutocompleteBinding {
  ecl: string;           // The ECL query to execute
  branchPath: string;    // The branch path for the edition
  title?: string;        // Optional title for the form field
  note?: string;         // Optional hint text
}

@Component({
    selector: 'app-autocomplete-binding',
    templateUrl: './autocomplete-binding.component.html',
    styleUrls: ['./autocomplete-binding.component.css'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => AutocompleteBindingComponent),
            multi: true
        },
        {
            provide: MatFormFieldControl,
            useExisting: AutocompleteBindingComponent
        }
    ],
    standalone: false
})
export class AutocompleteBindingComponent implements OnInit, OnChanges, ControlValueAccessor  {
  @Input() binding: AutocompleteBinding;
  @Input() term: string = "";
  @Output() selectionChange = new EventEmitter<any>();
  @Output() cleared = new EventEmitter<any>();
  
  formControl = new UntypedFormControl();
  autoFilter: Observable<any> | undefined;
  loading = false;
  selectedConcept: any = {};

  static nextId = 0;
  stateChanges = new Subject<void>();
  focused = false;
  ngControl = null;
  errorState = false;
  controlType = 'app-autocomplete-binding';
  id = `app-autocomplete-binding-${AutocompleteBindingComponent.nextId++}`;
  describedBy = '';

  constructor(private terminologyService: TerminologyService) { }

  private onChange: (value: any) => void = () => {};

  private onTouched: () => void = () => {};

  get empty() {
    return !this.formControl.value;
  }

  setDescribedByIds(ids: string[]): void {
    this.describedBy = ids.join(' ');
  }

  onContainerClick(event: MouseEvent): void {
    // Handle the action when the container is clicked
  }
  
  writeValue(value: any): void {
    if (value && typeof value === 'object' && value.display) {
        this.formControl.setValue(value.display, { emitEvent: false });
    } else {
        this.formControl.setValue(value, { emitEvent: false });
    }
  }

  registerOnChange(fn: (value: any) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['term']) {
        this.term = changes['term'].currentValue;
        let localTerm = this.term;
        if (this.term && typeof this.term === 'object' && localTerm["display"]) {
            this.formControl.setValue(localTerm["display"]);
        } else {
            this.formControl.setValue(this.term);
        }
    }
  }


  ngOnInit(): void {
      this.autoFilter = this.formControl.valueChanges.pipe(
        debounceTime(300),
        distinctUntilChanged(),
        /** 1️⃣  Launch request only when term ≥ 3 chars */
        switchMap((term: string) => {
          if (term?.length >= 3 && this.binding?.branchPath) {
            this.loading = true;
            /** 2️⃣  Emit [] immediately, then emit the server result */
            return concat(
              of([]),                                                   // clears the panel
              this.terminologyService
                  .runECL(this.binding.branchPath, this.binding.ecl, term, 0, 50)
                  .pipe(
                    map(r => {
                      // Transform native API response to expected format
                      const items = r.items ?? [];
                      return items.map((item: any) => ({
                        code: item.conceptId,
                        display: item.fsn?.term || `Concept ${item.conceptId}`
                      }));
                    }),
                    finalize(() => (this.loading = false))              // turn spinner off
                  )
            );
          }
          /** 3️⃣  Short queries or missing branchPath → always empty */
          return of([]);
        })
      );
  }

  onTermChange() {
    this.formControl.setValue(this.term);
  }

  optionSelected(value: any) {
    this.selectedConcept = value;
    this.selectionChange.emit(value);
    this.onChange(value);
  }

  clearInput() {
    this.formControl.reset();
    this.selectedConcept = { code: '', display:''};
    this.selectionChange.emit(this.selectedConcept);
    this.cleared.emit(null);
  }

  change(event: any) {
    const item = event?.option?.value;
    if (item) {
      this.optionSelected({ code: item.code, display: item.display });
      this.formControl.setValue(item.display); // Set the form control's value to the selected option's display
    }
  }

}

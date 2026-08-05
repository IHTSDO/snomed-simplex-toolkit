import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { EclBuilderDialogComponent } from './ecl-builder-dialog.component';
import { EclBuilderComponent } from '../ecl-builder.component';
import { ExpressionConstraintNodeComponent } from '../expression-constraint-node/expression-constraint-node.component';
import { AttributeSetEditorComponent } from '../attribute-set-editor/attribute-set-editor.component';
import { AttributeEditorComponent } from '../attribute-editor/attribute-editor.component';
import { ConceptPickerComponent } from '../concept-picker/concept-picker.component';
import { ConstraintOperatorSelectComponent } from '../constraint-operator-select/constraint-operator-select.component';
import { AutocompleteBindingComponent } from '../../binding-controls/autocomplete-binding/autocomplete-binding.component';
import { EclBuilderService } from '../ecl-builder.service';
import { TerminologyService } from '../../../services/simplex/terminology.service';

describe('EclBuilderDialogComponent', () => {
  let component: EclBuilderDialogComponent;
  let fixture: ComponentFixture<EclBuilderDialogComponent>;
  let eclBuilderServiceSpy: jasmine.SpyObj<EclBuilderService>;
  let terminologyServiceSpy: jasmine.SpyObj<TerminologyService>;

  beforeEach(async () => {
    eclBuilderServiceSpy = jasmine.createSpyObj<EclBuilderService>('EclBuilderService', [
      'stringToModel',
      'modelToString',
      'collectUnsupportedFeatures',
      'getDomainAttributes',
      'addRefinement',
      'createEmptyAttribute',
      'setBranchPath'
    ]);
    terminologyServiceSpy = jasmine.createSpyObj<TerminologyService>('TerminologyService', [
      'runECL',
      'getComputedLanguageContext'
    ]);

    eclBuilderServiceSpy.stringToModel.and.returnValue(of({ wildcard: true, conceptId: '*', uiId: '1' } as any));
    eclBuilderServiceSpy.modelToString.and.returnValue(of('*'));
    eclBuilderServiceSpy.collectUnsupportedFeatures.and.returnValue([]);
    eclBuilderServiceSpy.getDomainAttributes.and.returnValue(of([]));
    eclBuilderServiceSpy.createEmptyAttribute.and.returnValue({
      attributeName: { conceptId: '', operator: 'descendantorselfof' },
      expressionComparisonOperator: '=',
      value: { conceptId: '', operator: 'descendantorselfof' }
    } as any);

    terminologyServiceSpy.runECL.and.returnValue(of({ items: [] }));
    terminologyServiceSpy.getComputedLanguageContext.and.returnValue('en');

    await TestBed.configureTestingModule({
      declarations: [
        EclBuilderDialogComponent,
        EclBuilderComponent,
        ExpressionConstraintNodeComponent,
        AttributeSetEditorComponent,
        AttributeEditorComponent,
        ConceptPickerComponent,
        ConstraintOperatorSelectComponent,
        AutocompleteBindingComponent
      ],
      imports: [
        BrowserAnimationsModule,
        NoopAnimationsModule,
        FormsModule,
        ReactiveFormsModule,
        HttpClientTestingModule,
        MatDialogModule,
        MatButtonModule,
        MatButtonToggleModule,
        MatCardModule,
        MatFormFieldModule,
        MatSelectModule,
        MatInputModule,
        MatIconModule,
        MatAutocompleteModule,
        MatProgressSpinnerModule,
        MatMenuModule
      ],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { ecl: '', branchPath: 'MAIN' } },
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } },
        { provide: EclBuilderService, useValue: eclBuilderServiceSpy },
        { provide: TerminologyService, useValue: terminologyServiceSpy }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(EclBuilderDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should enable confirm in plain text mode when ECL is non-empty', () => {
    component.setMode('plain');
    component.localEcl = '<< 195967001';
    expect(component.canConfirm).toBeTrue();
  });

  it('should auto-switch to plain text for unsupported ECL on init', () => {
    eclBuilderServiceSpy.stringToModel.and.returnValue(of({
      exclusionExpressionConstraints: { first: { conceptId: '123' } },
      uiId: '1'
    } as any));
    eclBuilderServiceSpy.collectUnsupportedFeatures.and.returnValue(['exclusion expression constraints']);

    const unsupportedFixture = TestBed.createComponent(EclBuilderDialogComponent);
    unsupportedFixture.componentInstance.data = { ecl: '<< 123 MINUS << 456', branchPath: 'MAIN' };
    unsupportedFixture.componentInstance.localEcl = '<< 123 MINUS << 456';
    unsupportedFixture.componentInstance.ngOnInit();
    unsupportedFixture.detectChanges();

    expect(unsupportedFixture.componentInstance.mode).toBe('plain');
  });

  it('should keep plain text mode when switching to builder with unsupported ECL', () => {
    component.setMode('plain');
    component.localEcl = '<< 123 MINUS << 456';
    eclBuilderServiceSpy.collectUnsupportedFeatures.and.returnValue(['exclusion expression constraints']);

    component.setMode('builder');

    expect(component.mode).toBe('plain');
    expect(component.modeSwitchWarning).toContain('not supported by the visual builder');
  });

  it('should keep plain text mode when parse fails switching to builder', () => {
    component.setMode('plain');
    component.localEcl = 'not valid ecl';
    eclBuilderServiceSpy.stringToModel.and.returnValue(throwError(() => new Error('parse failed')));

    component.setMode('builder');

    expect(component.mode).toBe('plain');
    expect(component.modeSwitchWarning).toContain('Unable to parse');
  });
});

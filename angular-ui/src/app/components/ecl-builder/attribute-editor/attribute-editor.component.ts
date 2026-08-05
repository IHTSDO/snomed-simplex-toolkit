import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { EclBuilderService } from '../ecl-builder.service';
import { EclAttribute, EclComparisonOperator, EclDomainAttribute, EclOperator } from '../ecl-builder.models';

@Component({
  selector: 'app-attribute-editor',
  templateUrl: './attribute-editor.component.html',
  styleUrls: ['./attribute-editor.component.scss'],
  standalone: false
})
export class AttributeEditorComponent implements OnChanges {
  @Input({ required: true }) attribute!: EclAttribute;
  @Input() branchPath = 'MAIN';
  @Input() focusConceptId = '';
  @Input() focusConceptOperator = '';
  @Input() readonly = false;
  @Output() changed = new EventEmitter<void>();
  @Output() remove = new EventEmitter<void>();

  domainAttributes: EclDomainAttribute[] = [];
  filteredDomainAttributes: EclDomainAttribute[] = [];
  attributeNameFilter = '';
  loadingDomainAttributes = false;
  mrcmFailed = false;

  readonly comparisonOperators: EclComparisonOperator[] = ['=', '!='];

  constructor(private eclBuilderService: EclBuilderService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['branchPath']) {
      this.eclBuilderService.setBranchPath(this.branchPath);
    }

    if (changes['focusConceptId'] || changes['focusConceptOperator'] || changes['branchPath']) {
      this.loadDomainAttributes();
    }
  }

  get selectedAttributeConceptId(): string {
    return this.extractConceptId(this.attribute.attributeName.conceptId);
  }

  updateAttributeNameSelection(conceptId: string): void {
    const matching = this.domainAttributes.find((item) => item.conceptId === conceptId);
    if (!matching) {
      this.attribute.attributeName.conceptId = '';
      this.attribute.attributeName.term = '';
      this.changed.emit();
      return;
    }

    const display = matching.fsn?.term || '';
    this.attribute.attributeName.conceptId = display ? `${conceptId} |${display}|` : conceptId;
    this.attribute.attributeName.term = display;
    this.attribute.attributeName.wildcard = false;
    this.attribute.attributeName.operator = this.attribute.attributeName.operator ?? 'descendantorselfof';
    this.changed.emit();
  }

  updateAttributeNameFilter(value: string): void {
    this.attributeNameFilter = value;
    this.applyAttributeFilter();
  }

  updateValue(conceptId: string): void {
    const valueExpression = this.getValueExpression();
    valueExpression.conceptId = conceptId;
    valueExpression.wildcard = conceptId === '*';
    valueExpression.operator = conceptId === '*' ? undefined : (valueExpression.operator ?? 'descendantorselfof');
    this.changed.emit();
  }

  updateValueOperator(operator: string): void {
    const valueExpression = this.getValueExpression();
    valueExpression.operator = operator as EclOperator;
    this.changed.emit();
  }

  get valueOperatorSelection(): EclOperator {
    return (this.getValueExpression().operator ?? '') as EclOperator;
  }

  get valueConceptId(): string {
    return this.getValueExpression().conceptId ?? '';
  }

  getAttributeRangeEcl(): string {
    if (this.mrcmFailed) {
      return '<< 138875005';
    }
    const conceptId = this.extractConceptId(this.attribute.attributeName.conceptId);
    const matching = this.domainAttributes.find((item) => item.conceptId === conceptId);
    return matching?.attributeRange?.[0]?.rangeConstraint || '<< 138875005';
  }

  getAttributeNameHint(): string {
    if (this.mrcmFailed) {
      return '';
    }
    return this.domainAttributes.length ? 'Restricted by MRCM' : '';
  }

  getValueNote(): string {
    if (this.mrcmFailed) {
      return 'Any SNOMED concept';
    }
    const rangeEcl = this.getAttributeRangeEcl();
    return rangeEcl && rangeEcl !== '<< 138875005' ? 'Constrained by attribute range' : '';
  }

  private loadDomainAttributes(): void {
    const conceptId = this.extractConceptId(this.focusConceptId);
    if (!conceptId || conceptId === '*' || this.focusConceptOperator === 'memberOf') {
      this.domainAttributes = [];
      this.filteredDomainAttributes = [];
      return;
    }

    this.mrcmFailed = false;
    this.loadingDomainAttributes = true;
    this.eclBuilderService.setBranchPath(this.branchPath);
    this.eclBuilderService.getDomainAttributes(conceptId).subscribe({
      next: (attributes) => {
        this.domainAttributes = attributes;
        this.applyAttributeFilter();
        this.loadingDomainAttributes = false;
      },
      error: () => {
        this.mrcmFailed = true;
        this.domainAttributes = [];
        this.filteredDomainAttributes = [];
        this.loadingDomainAttributes = false;
      }
    });
  }

  private applyAttributeFilter(): void {
    const query = this.attributeNameFilter.trim().toLowerCase();
    if (!query) {
      this.filteredDomainAttributes = [...this.domainAttributes];
      return;
    }

    this.filteredDomainAttributes = this.domainAttributes.filter((item) => {
      const label = (item.fsn?.term || item.conceptId).toLowerCase();
      return label.includes(query) || item.conceptId.includes(query);
    });
  }

  private extractConceptId(conceptIdAndTerm?: string): string {
    if (!conceptIdAndTerm) {
      return '';
    }

    if (conceptIdAndTerm.includes('|')) {
      return conceptIdAndTerm.substring(0, conceptIdAndTerm.indexOf('|')).trim();
    }

    return conceptIdAndTerm.trim();
  }

  private getValueExpression(): EclAttribute['value'] {
    return this.attribute.value?.nestedExpressionConstraint ?? this.attribute.value;
  }
}

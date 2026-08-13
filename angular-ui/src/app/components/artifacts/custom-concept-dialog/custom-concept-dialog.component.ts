import { Component, Inject, OnInit } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from 'src/app/services/simplex/simplex.service';

/** US English language reference set — matches backend Concepts.US_LANG_REFSET */
export const US_LANG_REFSET = '900000000000509007';

export interface CustomConceptLangRefset {
	refsetId: string;
	label: string;
}

export interface CustomConceptDialogData {
	edition: string;
	editionDetails: any;
	conceptId?: string;
}

@Component({
	selector: 'app-custom-concept-dialog',
	templateUrl: './custom-concept-dialog.component.html',
	styleUrl: './custom-concept-dialog.component.scss'
})
export class CustomConceptDialogComponent implements OnInit {
	form: FormGroup;
	loading = false;
	saving = false;
	langRefsets: CustomConceptLangRefset[] = [];
	parentTerm = '';
	parentBinding: { ecl: string; branchPath: string } | null = null;
	readonly isEditMode: boolean;

	get conceptsMaintainedExternally(): boolean {
		return !!this.data.editionDetails?.conceptsMaintainedExternally;
	}

	constructor(
		public dialogRef: MatDialogRef<CustomConceptDialogComponent>,
		@Inject(MAT_DIALOG_DATA) public data: CustomConceptDialogData,
		private fb: FormBuilder,
		private simplexService: SimplexService,
		private snackBar: MatSnackBar
	) {
		this.isEditMode = !!data.conceptId;
		this.form = this.fb.group({
			parentCode: ['', Validators.required],
			active: [true],
			conceptCode: [''],
			langRefsets: this.fb.group({})
		});
	}

	ngOnInit(): void {
		const branchPath = this.data.editionDetails?.branchPath || this.data.editionDetails?.simplexWorkingBranch;
		if (branchPath) {
			this.parentBinding = { ecl: '<< 138875005', branchPath };
		}
		if (this.isEditMode) {
			this.loadConcept();
		} else {
			this.loadCreateForm();
		}
	}

	private loadCreateForm(): void {
		this.loading = true;
		this.simplexService.getTranslations(this.data.edition).subscribe({
			next: (translations: any[]) => {
				this.langRefsets = [
					{ refsetId: US_LANG_REFSET, label: 'English, US dialect' },
					...(translations || []).map((t) => ({
						refsetId: t.conceptId || t.id,
						label: t.pt?.term || t.fsn?.term || t.conceptId
					}))
				];
				this.buildLangRefsetControls({});
				this.loading = false;
			},
			error: () => {
				this.snackBar.open('Failed to load translation languages.', 'Dismiss', { duration: 6000 });
				this.loading = false;
			}
		});
	}

	private loadConcept(): void {
		this.loading = true;
		this.simplexService.getCustomConcept(this.data.edition, this.data.conceptId!).subscribe({
			next: (detail: any) => {
				this.langRefsets = detail.langRefsets || [];
				this.parentTerm = detail.parentTerm
					? `${detail.parentCode} |${detail.parentTerm}|`
					: detail.parentCode || '';
				this.form.patchValue({
					parentCode: detail.parentCode || '',
					active: detail.active !== false,
					conceptCode: detail.conceptId || ''
				});
				if (this.isEditMode) {
					this.form.get('conceptCode')?.disable();
				}
				this.buildLangRefsetControls(detail.langRefsetTerms || {});
				this.loading = false;
			},
			error: (err) => {
				this.snackBar.open(this.errorMessage(err, 'Failed to load concept.'), 'Dismiss', { duration: 6000 });
				this.loading = false;
			}
		});
	}

	private buildLangRefsetControls(langRefsetTerms: Record<string, string[]>): void {
		const group = this.fb.group({});
		for (const refset of this.langRefsets) {
			const terms = langRefsetTerms[refset.refsetId] || [];
			const preferred = terms[0] || '';
			const synonyms = terms.slice(1);
			const refsetGroup = this.fb.group({
				preferred: [preferred, refset.refsetId === US_LANG_REFSET ? Validators.required : []],
				synonyms: this.fb.array(synonyms.map((term) => this.fb.control(term)))
			});
			group.addControl(refset.refsetId, refsetGroup);
		}
		this.form.setControl('langRefsets', group);
	}

	synonymsArray(refsetId: string): FormArray {
		return (this.form.get(['langRefsets', refsetId, 'synonyms']) as FormArray) || this.fb.array([]);
	}

	addSynonym(refsetId: string): void {
		this.synonymsArray(refsetId).push(this.fb.control(''));
	}

	removeSynonym(refsetId: string, index: number): void {
		this.synonymsArray(refsetId).removeAt(index);
	}

	onParentSelectionChange(selection: { code?: string; display?: string }): void {
		this.form.patchValue({ parentCode: selection?.code || '' });
	}

	get dialogTitle(): string {
		return this.isEditMode ? 'Edit concept' : 'Create concept';
	}

	canSubmit(): boolean {
		if (this.loading || this.saving) {
			return false;
		}
		if (!this.form.get('active')?.value) {
			return this.isEditMode;
		}
		return this.form.valid;
	}

	onSubmit(): void {
		if (!this.canSubmit()) {
			return;
		}
		const active = !!this.form.get('active')?.value;
		const body = {
			parentCode: active ? (this.form.get('parentCode')?.value || '').trim() : null,
			active,
			conceptCode: this.conceptsMaintainedExternally && !this.isEditMode
				? (this.form.get('conceptCode')?.value || '').trim() || null
				: null,
			langRefsetTerms: active ? this.buildLangRefsetTermsPayload() : {}
		};

		this.saving = true;
		const request$ = this.isEditMode
			? this.simplexService.updateCustomConcept(this.data.edition, this.data.conceptId!, body)
			: this.simplexService.createCustomConcept(this.data.edition, body as any);

		request$.subscribe({
			next: () => {
				this.saving = false;
				this.dialogRef.close(true);
			},
			error: (err) => {
				this.saving = false;
				this.snackBar.open(this.errorMessage(err, 'Failed to save concept.'), 'Dismiss', { duration: 8000 });
			}
		});
	}

	private buildLangRefsetTermsPayload(): Record<string, string[]> {
		const payload: Record<string, string[]> = {};
		const langRefsetsGroup = this.form.get('langRefsets') as FormGroup;
		for (const refset of this.langRefsets) {
			const refsetGroup = langRefsetsGroup.get(refset.refsetId) as FormGroup;
			if (!refsetGroup) {
				continue;
			}
			const preferred = (refsetGroup.get('preferred')?.value || '').trim();
			const synonyms = (refsetGroup.get('synonyms') as FormArray).controls
				.map((control) => (control.value || '').trim())
				.filter((term) => term.length > 0);
			const terms = preferred ? [preferred, ...synonyms] : synonyms;
			if (terms.length > 0) {
				payload[refset.refsetId] = terms;
			}
		}
		return payload;
	}

	onCancel(): void {
		this.dialogRef.close(false);
	}

	private errorMessage(err: any, fallback: string): string {
		return err?.error?.message || err?.message || fallback;
	}
}

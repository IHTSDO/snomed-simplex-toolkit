import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormsModule } from '@angular/forms';
import { SimplexService } from '../../services/simplex/simplex.service';
import { HttpClient } from '@angular/common/http';
import { LanguagePolicyQuestionnaireComponent } from '../language-policy-questionnaire/language-policy-questionnaire.component';
import { LanguageTranslationPolicy } from '../../models/language-translation-policy.model';

export interface SetupAiTranslationDialogData {
	edition: string;
	refsetId: string;
	labelSetName: string;
	label: string;
	dialectName?: string;
	selectedLabelSet?: any;
}

export interface GoldenExample {
	conceptId: string;
	preferredTerm: string;
	translation: string;
	suggestions?: string[];
}

@Component({
	selector: 'app-setup-ai-translation-dialog',
	standalone: true,
	imports: [
		CommonModule,
		MatDialogModule,
		MatButtonModule,
		MatFormFieldModule,
		MatInputModule,
		MatProgressSpinnerModule,
		FormsModule,
		LanguagePolicyQuestionnaireComponent
	],
	templateUrl: './setup-ai-translation-dialog.component.html',
	styleUrl: './setup-ai-translation-dialog.component.scss'
})
export class SetupAiTranslationDialogComponent implements OnInit {
	currentStep: 'policy' | 'golden' = 'golden';
	policyConfigured = false;
	policyExists = false;
	showPolicyStep = false;
	reviewPolicy = false;
	loadingPolicy = true;
	loading = false;
	loadingExamples = false;
	loadingSuggestions = false;
	loadingNewExample = false;
	policySaved = false;
	goldenExamples: GoldenExample[] = [];
	existingPolicy: LanguageTranslationPolicy | null = null;

	constructor(
		public dialogRef: MatDialogRef<SetupAiTranslationDialogComponent>,
		@Inject(MAT_DIALOG_DATA) public data: SetupAiTranslationDialogData,
		private snackBar: MatSnackBar,
		private simplexService: SimplexService,
		private http: HttpClient
	) {}

	ngOnInit(): void {
		this.loadPolicyStatus();
		this.loadSampleRows();
	}

	get dialectName(): string {
		return this.data.dialectName || this.data.labelSetName;
	}

	loadPolicyStatus(): void {
		this.loadingPolicy = true;
		this.simplexService.getLanguagePolicy(this.data.edition, this.data.refsetId).subscribe({
			next: (policy) => {
				this.existingPolicy = policy;
				this.policyExists = true;
				this.policyConfigured = true;
				this.policySaved = true;
				this.showPolicyStep = false;
				this.currentStep = 'golden';
				this.loadingPolicy = false;
				this.checkAndLoadSuggestions();
			},
			error: (err) => {
				this.loadingPolicy = false;
				if (err?.status === 404) {
					this.policyExists = false;
					this.showPolicyStep = true;
					this.currentStep = 'policy';
				} else {
					this.snackBar.open('Failed to check language policy.', 'Close', { duration: 4000 });
					this.currentStep = 'golden';
				}
			}
		});
	}

	loadSampleRows(): void {
		this.loadingExamples = true;
		const apiUrl = `api/${this.data.edition}/translations/${this.data.refsetId}/snolate-set/${this.data.label}/rows?page=0&size=1000`;

		this.http.get(apiUrl).subscribe({
			next: (response: any) => {
				this.loadingExamples = false;
				this.populateGoldenExamples(response.results);
				this.checkAndLoadSuggestions();
			},
			error: () => {
				this.loadingExamples = false;
				this.snackBar.open('Failed to load sample concepts. Starting with empty examples.', 'Close', { duration: 3000 });
				while (this.goldenExamples.length < 5) {
					this.pushEmptyExample();
				}
				this.checkAndLoadSuggestions();
			}
		});
	}

	onPolicySaved(_policy: LanguageTranslationPolicy): void {
		this.policyConfigured = true;
		this.policySaved = true;
		this.policyExists = true;
		this.reviewPolicy = false;
		this.showPolicyStep = false;
		this.currentStep = 'golden';
		this.checkAndLoadSuggestions();
		this.snackBar.open('Language policy saved.', 'Close', { duration: 3000 });
	}

	onPolicyCancelled(): void {
		if (!this.policyConfigured) {
			this.dialogRef.close();
		} else {
			this.reviewPolicy = false;
			this.showPolicyStep = false;
			this.currentStep = 'golden';
		}
	}

	openPolicyReview(): void {
		this.reviewPolicy = true;
		this.showPolicyStep = true;
		this.currentStep = 'policy';
	}

	loadTranslationSuggestions(): void {
		const englishTerms = this.goldenExamples
			.filter(example => example.preferredTerm.trim() && !example.translation.trim())
			.map(example => example.preferredTerm.trim());

		if (englishTerms.length === 0) {
			return;
		}

		this.loadingSuggestions = true;
		const apiUrl = `api/${this.data.edition}/translations/${this.data.refsetId}/snolate-set/${this.data.label}/ai-suggestion`;

		this.http.post(apiUrl, englishTerms).subscribe({
			next: (response: any) => {
				this.loadingSuggestions = false;
				this.goldenExamples.forEach(example => {
					if (example.preferredTerm.trim() && !example.translation.trim()) {
						const suggestions = response[example.preferredTerm.trim()];
						if (suggestions?.length > 0) {
							example.suggestions = suggestions;
						}
					}
				});
			},
			error: () => {
				this.loadingSuggestions = false;
			}
		});
	}

	populateGoldenExamples(sampleRows: any[]): void {
		this.goldenExamples = [];
		const existingAiGoldenSet = this.data.selectedLabelSet?.aiGoldenSet || {};
		const existingExamples: GoldenExample[] = [];

		Object.keys(existingAiGoldenSet).forEach(key => {
			const translation = existingAiGoldenSet[key];
			if (translation?.trim()) {
				const [conceptId, preferredTerm] = key.split('|');
				if (conceptId && preferredTerm) {
					existingExamples.push({ conceptId, preferredTerm, translation });
				}
			}
		});

		existingExamples.forEach(example => this.goldenExamples.push(example));

		const remainingSlots = 5 - this.goldenExamples.length;
		if (remainingSlots > 0 && sampleRows.length > 0) {
			const startIndex = existingExamples.length;
			const offset = Math.floor(sampleRows.length / 5);
			for (let i = 0; i < remainingSlots; i++) {
				const row = sampleRows[(startIndex + i) * offset];
				if (row) {
					this.goldenExamples.push({
						conceptId: row.context || row.key || '',
						preferredTerm: row.source?.[0] || '',
						translation: row.target?.[0] || '',
					});
				} else {
					this.pushEmptyExample();
				}
			}
		}

		while (this.goldenExamples.length < 5) {
			this.pushEmptyExample();
		}
	}

	private pushEmptyExample(): void {
		this.goldenExamples.push({
			conceptId: '',
			preferredTerm: '',
			translation: ''
		});
	}

	onCancel(): void {
		this.dialogRef.close();
	}

	checkAndLoadSuggestions(): void {
		if (this.policySaved && !this.loadingExamples && this.goldenExamples.length > 0 && this.currentStep === 'golden') {
			this.loadTranslationSuggestions();
		}
	}

	addExample(): void {
		const conceptId = prompt('Enter Concept ID:');
		if (!conceptId?.trim()) {
			return;
		}

		this.loadingNewExample = true;
		const apiUrl = `api/${this.data.edition}/translations/${this.data.refsetId}/snolate-set/${this.data.label}/unit/${conceptId.trim()}`;

		this.http.get(apiUrl).subscribe({
			next: (response: any) => {
				this.loadingNewExample = false;
				this.goldenExamples.push({
					conceptId: response.context || response.key || conceptId.trim(),
					preferredTerm: response.source?.[0] || '',
					translation: response.target?.[0] || '',
				});
				if (this.policySaved) {
					this.loadTranslationSuggestions();
				}
			},
			error: () => {
				this.loadingNewExample = false;
				this.snackBar.open(`Failed to load example for concept ID: ${conceptId}`, 'Close', { duration: 5000 });
			}
		});
	}

	removeExample(index: number): void {
		if (this.goldenExamples.length > 1) {
			this.goldenExamples.splice(index, 1);
		}
	}

	selectSuggestion(example: GoldenExample, suggestion: string): void {
		example.translation = suggestion;
		example.suggestions = undefined;
	}

	onSetup(): void {
		const validExamples = this.goldenExamples.filter(example =>
			example.conceptId.trim() && example.preferredTerm.trim() && example.translation.trim()
		);

		if (validExamples.length === 0) {
			this.snackBar.open('Please add at least one golden example', 'Close', { duration: 3000 });
			return;
		}

		if (!this.policySaved) {
			this.snackBar.open('Please complete the language policy first.', 'Close', { duration: 3000 });
			return;
		}

		this.loading = true;
		this.saveAiSetup(validExamples);
	}

	saveAiSetup(goldenExamples: GoldenExample[]): void {
		const aiGoldenSet: { [key: string]: string } = {};
		goldenExamples.forEach(example => {
			aiGoldenSet[`${example.conceptId}|${example.preferredTerm}`] = example.translation;
		});

		const apiUrl = `api/${this.data.edition}/translations/${this.data.refsetId}/snolate-set/${this.data.label}/ai-setup`;
		this.http.post(apiUrl, { aiGoldenSet }).subscribe({
			next: () => {
				this.loading = false;
				this.dialogRef.close({ action: 'setup', goldenExamples });
			},
			error: () => {
				this.loading = false;
				this.snackBar.open('Failed to save AI setup. Please try again.', 'Close', { duration: 5000 });
			}
		});
	}
}

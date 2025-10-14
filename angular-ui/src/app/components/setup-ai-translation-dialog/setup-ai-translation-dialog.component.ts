import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { SimplexService } from '../../services/simplex/simplex.service';
import { HttpClient } from '@angular/common/http';

export interface SetupAiTranslationDialogData {
	edition: string;
	refsetId: string;
	labelSetName: string;
	label: string;
	selectedLabelSet?: any;
}

export interface LanguageRules {
	highLevelInfo: string;
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
		ReactiveFormsModule,
		FormsModule
	],
	template: `
		<h2 mat-dialog-title>
			{{ currentStep === 1 ? 'Create/Review language level rules' : 'Create Golden Examples' }}
		</h2>
		<mat-dialog-content>
			<!-- Step 1: Language Level Rules -->
			<div *ngIf="currentStep === 1">
				<p>Add high level information about the language for: <strong>{{ data.labelSetName }}</strong></p>
				
				<form [formGroup]="languageRulesForm" class="language-rules-form">
					<mat-form-field appearance="outline" class="full-width">
						<mat-label>Language Level Information</mat-label>
						<textarea 
							matInput 
							formControlName="highLevelInfo"
							placeholder="Enter high level information about the language, translation guidelines, etc."
							rows="8">
						</textarea>
					</mat-form-field>
				</form>
			</div>
			
			<!-- Step 2: Golden Examples -->
			<div *ngIf="currentStep === 2">
				<div *ngIf="loadingExamples" class="loading-container">
					<mat-spinner diameter="40"></mat-spinner>
					<p>Loading sample concepts...</p>
				</div>
				
				<div *ngIf="!loadingExamples" class="golden-examples-section">
					<h4>Golden Examples for <strong>{{ data.labelSetName }}</strong></h4>
					<p class="section-description">
						Let us create five strong examples to demonstrate the correct translation approach for this set.
					</p>
					
					<div class="examples-list">
						<div *ngFor="let example of goldenExamples; let i = index" class="example-item">
							<div class="example-header">
								<span class="example-number">{{ i + 1 }}</span>
								<button 
									mat-icon-button 
									color="warn" 
									(click)="removeExample(i)"
									class="remove-button">
									×
								</button>
							</div>
							<div class="example-content">
								<div class="example-field">
									<label>Concept ID:</label>
									<input 
										[(ngModel)]="example.conceptId" 
										placeholder="e.g., 123456789"
										class="concept-input"
										readonly>
								</div>
								<div class="example-field">
									<label>Preferred Term:</label>
									<input 
										[(ngModel)]="example.preferredTerm" 
										placeholder="e.g., Heart disease"
										class="term-input"
										readonly>
								</div>
								<div class="example-field">
									<label>Translation:</label>
									<input 
										[(ngModel)]="example.translation" 
										placeholder="e.g., Maladie cardiaque"
										class="translation-input">
									
									<!-- AI Translation Suggestions Loading -->
									<div *ngIf="loadingSuggestions && example.preferredTerm.trim() && !example.translation.trim()" class="suggestions-loading">
										<mat-spinner diameter="16"></mat-spinner>
										<span class="loading-text">Loading AI suggestions...</span>
									</div>
									
									<!-- AI Translation Suggestions -->
									<div *ngIf="!loadingSuggestions && example.suggestions && example.suggestions.length > 0" class="suggestions-container">
										<label class="suggestions-label">AI Suggestions:</label>
										<div class="suggestions-list">
											<button 
												*ngFor="let suggestion of example.suggestions" 
												type="button"
												class="suggestion-button"
												(click)="selectSuggestion(example, suggestion)">
												{{ suggestion }}
											</button>
										</div>
									</div>
								</div>
							</div>
						</div>
					</div>
					
					<button 
						mat-stroked-button 
						color="primary" 
						[disabled]="loadingNewExample"
						(click)="addExample()"
						class="add-example-button">
						<span *ngIf="loadingNewExample" class="loading-content">
							<mat-spinner diameter="16"></mat-spinner>
							Loading example...
						</span>
						<span *ngIf="!loadingNewExample">+ Add Golden Example</span>
					</button>
				</div>
			</div>
		</mat-dialog-content>
		
		<mat-dialog-actions align="end">
			<button mat-button (click)="onCancel()">Cancel</button>
			
			<!-- Back button for step 2 -->
			<button 
				*ngIf="currentStep === 2"
				mat-button 
				(click)="goBack()">
				Back
			</button>
			
			<!-- Next button for step 1 -->
			<button 
				*ngIf="currentStep === 1"
				mat-raised-button 
				color="primary" 
				[disabled]="loadingExamples"
				(click)="nextStep()">
				<span *ngIf="loadingExamples" class="loading-content">
					<mat-spinner diameter="20"></mat-spinner>
					Loading samples for Step 2...
				</span>
				<span *ngIf="!loadingExamples">Next</span>
			</button>
			
			<!-- Setup button for step 2 -->
			<button 
				*ngIf="currentStep === 2"
				mat-raised-button 
				color="primary" 
				[disabled]="loading"
				(click)="onSetup()">
				<span *ngIf="loading" class="loading-content">
					<mat-spinner diameter="20"></mat-spinner>
					Setting up...
				</span>
				<span *ngIf="!loading">Complete Setup</span>
			</button>
		</mat-dialog-actions>
	`,
	styles: [`
		.loading-container {
			display: flex;
			flex-direction: column;
			align-items: center;
			padding: 20px;
		}
		
		.language-rules-form {
			margin-top: 16px;
		}
		
		.full-width {
			width: 100%;
		}
		
		.step-summary {
			margin-bottom: 24px;
			padding: 16px;
			background-color: #f8f9fa;
			border-radius: 4px;
			border-left: 4px solid #1976d2;
		}
		
		.step-summary h4 {
			margin: 0 0 12px 0;
			color: #1976d2;
		}
		
		.summary-content {
			color: #333;
			white-space: pre-wrap;
			font-size: 0.9em;
			line-height: 1.4;
		}
		
		.golden-examples-section {
			margin-top: 10px;
		}
		
		.section-description {
			color: #666;
			font-size: 0.9em;
			margin-bottom: 16px;
		}
		
		.examples-list {
			margin-bottom: 16px;
		}
		
		.example-item {
			border: 1px solid #e0e0e0;
			border-radius: 4px;
			padding: 16px;
			margin-bottom: 12px;
			background-color: #fafafa;
		}
		
		.example-header {
			display: flex;
			justify-content: space-between;
			align-items: center;
			margin-bottom: 12px;
		}
		
		.example-number {
			font-weight: bold;
			color: #1976d2;
			background-color: #e3f2fd;
			padding: 4px 8px;
			border-radius: 12px;
			font-size: 0.8em;
		}
		
		.remove-button {
			width: 32px;
			height: 32px;
			line-height: 32px;
			font-size: 18px;
		}
		
		.example-content {
			display: flex;
			flex-direction: column;
			gap: 12px;
		}
		
		.example-field {
			display: flex;
			flex-direction: column;
			gap: 4px;
		}
		
		.example-field label {
			font-weight: 500;
			color: #333;
			font-size: 0.9em;
		}
		
		.example-field input {
			padding: 8px 12px;
			border: 1px solid #ddd;
			border-radius: 4px;
			font-size: 0.9em;
		}
		
		.example-field input:focus {
			outline: none;
			border-color: #1976d2;
			box-shadow: 0 0 0 2px rgba(25, 118, 210, 0.2);
		}
		
		.example-field input[readonly] {
			background-color: #f5f5f5;
			color: #666;
			cursor: not-allowed;
		}
		
		.example-field input[readonly]:focus {
			border-color: #ddd;
			box-shadow: none;
		}
		
		.add-example-button {
			width: 100%;
			margin-top: 8px;
		}
		
		.loading-content {
			display: flex;
			align-items: center;
			gap: 8px;
		}
		
		mat-dialog-content {
			min-width: 500px;
			max-height: 600px;
			overflow-y: auto;
		}
		
		.suggestions-container {
			margin-top: 8px;
		}
		
		.suggestions-label {
			font-size: 0.8em;
			color: #666;
			font-weight: 500;
			margin-bottom: 4px;
			display: block;
		}
		
		.suggestions-list {
			display: flex;
			flex-wrap: wrap;
			gap: 6px;
		}
		
		.suggestion-button {
			background-color: #e3f2fd;
			border: 1px solid #1976d2;
			color: #1976d2;
			padding: 4px 8px;
			border-radius: 12px;
			font-size: 0.8em;
			cursor: pointer;
			transition: all 0.2s ease;
		}
		
		.suggestion-button:hover {
			background-color: #1976d2;
			color: white;
		}
		
		.suggestion-button:active {
			transform: scale(0.95);
		}
		
		.suggestions-loading {
			display: flex;
			align-items: center;
			gap: 8px;
			margin-top: 8px;
			padding: 8px 12px;
			background-color: #f5f5f5;
			border-radius: 4px;
			border: 1px solid #e0e0e0;
		}
		
		.loading-text {
			font-size: 0.8em;
			color: #666;
			font-style: italic;
		}
	`]
})
export class SetupAiTranslationDialogComponent implements OnInit {
	currentStep = 1;
	loading = false;
	loadingExamples = false;
	loadingSuggestions = false;
	loadingNewExample = false;
	languageAdviceSaved = false;
	languageRules: LanguageRules = { highLevelInfo: '' };
	goldenExamples: GoldenExample[] = [];
	
	languageRulesForm: FormGroup;

	constructor(
		public dialogRef: MatDialogRef<SetupAiTranslationDialogComponent>,
		@Inject(MAT_DIALOG_DATA) public data: SetupAiTranslationDialogData,
		private snackBar: MatSnackBar,
		private fb: FormBuilder,
		private simplexService: SimplexService,
		private http: HttpClient
	) {
		this.languageRulesForm = this.fb.group({
			highLevelInfo: ['']
		});
	}

	ngOnInit(): void {
		// Prepopulate language advice if it exists
		if (this.data.selectedLabelSet?.aiLanguageAdvice) {
			this.languageRulesForm.patchValue({
				highLevelInfo: this.data.selectedLabelSet.aiLanguageAdvice
			});
		}
		
		// Fetch sample rows to prepopulate golden examples
		this.loadSampleRows();
	}

	loadSampleRows(): void {
		this.loadingExamples = true;
		
		// Call the API with pageSize parameter of 1000
		const apiUrl = `/api/${this.data.edition}/translations/${this.data.refsetId}/weblate-set/${this.data.label}/sample-rows?pageSize=1000`;
		
		this.http.get(apiUrl).subscribe({
			next: (response: any) => {
				this.loadingExamples = false;
				this.populateGoldenExamples(response.results);
				// Check if we can now load AI suggestions
				this.checkAndLoadSuggestions();
			},
			error: (error) => {
				console.error('Error loading sample rows:', error);
				this.loadingExamples = false;
				this.snackBar.open('Failed to load sample concepts. Starting with empty examples.', 'Close', {
					duration: 3000
				});
				// Initialize with one empty golden example as fallback
				this.addExample();
				// Check if we can now load AI suggestions
				this.checkAndLoadSuggestions();
			}
		});
	}

	loadTranslationSuggestions(): void {
		// Get all English terms that don't have translations yet
		const englishTerms = this.goldenExamples
			.filter(example => example.preferredTerm.trim() && !example.translation.trim())
			.map(example => example.preferredTerm.trim());

		if (englishTerms.length === 0) {
			return; // No terms need suggestions
		}

		this.loadingSuggestions = true;

		// Build the API URL
		const apiUrl = `/api/${this.data.edition}/translations/${this.data.refsetId}/weblate-set/${this.data.label}/ai-suggestion`;

		this.http.post(apiUrl, englishTerms).subscribe({
			next: (response: any) => {
				this.loadingSuggestions = false;
				// Update golden examples with suggestions
				this.goldenExamples.forEach(example => {
					if (example.preferredTerm.trim() && !example.translation.trim()) {
						const suggestions = response[example.preferredTerm.trim()];
						if (suggestions && suggestions.length > 0) {
							example.suggestions = suggestions;
						}
					}
				});
			},
			error: (error) => {
				console.error('Error loading translation suggestions:', error);
				this.loadingSuggestions = false;
				// Don't show error to user as this is not critical functionality
			}
		});
	}

	populateGoldenExamples(sampleRows: any[]): void {
		// Clear existing examples
		this.goldenExamples = [];
		
		// Get existing AI golden set from the selected label set
		const existingAiGoldenSet = this.data.selectedLabelSet?.aiGoldenSet || {};
		const existingExamples: GoldenExample[] = [];
		
		// Convert existing AI golden set to GoldenExample objects and filter out empty ones
		Object.keys(existingAiGoldenSet).forEach(key => {
			const translation = existingAiGoldenSet[key];
			if (translation && translation.trim()) {
				const [conceptId, preferredTerm] = key.split('|');
				if (conceptId && preferredTerm) {
					existingExamples.push({
						conceptId: conceptId,
						preferredTerm: preferredTerm,
						translation: translation,
						suggestions: undefined
					});
				}
			}
		});
		
		// Add existing examples first
		existingExamples.forEach(example => {
			this.goldenExamples.push(example);
		});
		
		// Calculate how many more examples we need to reach 5
		const remainingSlots = 5 - this.goldenExamples.length;
		
		if (remainingSlots > 0 && sampleRows.length > 0) {
			// Calculate the offset between selected records for the remaining slots
			// We need to select from positions that would have been used for the remaining slots
			const startIndex = existingExamples.length; // Start from where existing examples would have been
			const offset = Math.floor(sampleRows.length / 5);
			
			// Select remaining evenly distributed records
			for (let i = 0; i < remainingSlots; i++) {
				const index = (startIndex + i) * offset;
				const row = sampleRows[index];
				
				if (row) {
					const example: GoldenExample = {
						conceptId: row.context || row.key || '',
						preferredTerm: row.source && row.source.length > 0 ? row.source[0] : '',
						translation: row.target && row.target.length > 0 && row.target[0] ? row.target[0] : '',
						suggestions: undefined
					};
					this.goldenExamples.push(example);
				} else {
					// If we don't have enough records, add an empty example
					this.addExample();
				}
			}
		}
		
		// If we still don't have 5 examples, add empty ones
		while (this.goldenExamples.length < 5) {
			this.addExample();
		}
	}

	onCancel(): void {
		this.dialogRef.close();
	}

	nextStep(): void {
		this.languageRules = this.languageRulesForm.value;
		
		// Save language advice first, then proceed to step 2
		this.saveLanguageAdvice();
	}

	goBack(): void {
		this.currentStep = 1;
	}

	saveLanguageAdvice(): void {
		const languageAdvice = this.languageRules.highLevelInfo || '';
		
		// Build the API URL for saving language advice
		const apiUrl = `/api/${this.data.edition}/translations/${this.data.refsetId}/weblate-set/${this.data.label}/ai-language-advice`;
		
		// Prepare the request body
		const requestBody = {
			languageAdvice: languageAdvice
		};

		this.http.post(apiUrl, requestBody).subscribe({
			next: () => {
				// Language advice saved successfully, proceed to step 2
				this.languageAdviceSaved = true;
				this.currentStep = 2;
				// Check if we can now load AI suggestions
				this.checkAndLoadSuggestions();
			},
			error: (error) => {
				console.error('Error saving language advice:', error);
				this.snackBar.open('Failed to save language advice. Please try again.', 'Close', {
					duration: 5000
				});
			}
		});
	}

	checkAndLoadSuggestions(): void {
		// Only load AI suggestions if both conditions are met:
		// 1. Language advice has been saved
		// 2. Golden examples have been loaded (not loading and not empty)
		if (this.languageAdviceSaved && !this.loadingExamples && this.goldenExamples.length > 0) {
			this.loadTranslationSuggestions();
		}
	}

	addExample(): void {
		// Prompt user for concept ID
		const conceptId = prompt('Enter Concept ID:');
		
		if (!conceptId || conceptId.trim() === '') {
			return; // User cancelled or entered empty string
		}
		
		this.loadingNewExample = true;
		
		// Build the API URL to fetch the specific sample row
		const apiUrl = `/api/${this.data.edition}/translations/${this.data.refsetId}/weblate-set/${this.data.label}/sample-row/${conceptId.trim()}`;
		
		this.http.get(apiUrl).subscribe({
			next: (response: any) => {
				this.loadingNewExample = false;
				
				// Create new golden example from the fetched data
				const newExample: GoldenExample = {
					conceptId: response.context || response.key || conceptId.trim(),
					preferredTerm: response.source && response.source.length > 0 ? response.source[0] : '',
					translation: response.target && response.target.length > 0 && response.target[0] ? response.target[0] : '',
					suggestions: undefined
				};
				
				this.goldenExamples.push(newExample);
				
				// If we have language advice saved, load suggestions for this new example
				if (this.languageAdviceSaved) {
					this.loadTranslationSuggestions();
				}
			},
			error: (error) => {
				this.loadingNewExample = false;
				console.error('Error loading sample row:', error);
				this.snackBar.open(`Failed to load example for concept ID: ${conceptId}`, 'Close', {
					duration: 5000
				});
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
		// Clear suggestions after selection
		example.suggestions = undefined;
	}

	onSetup(): void {
		// Filter out empty examples
		const validExamples = this.goldenExamples.filter(example => 
			example.conceptId.trim() && 
			example.preferredTerm.trim() && 
			example.translation.trim()
		);

		if (validExamples.length === 0) {
			this.snackBar.open('Please add at least one golden example', 'Close', {
				duration: 3000
			});
			return;
		}

		this.loading = true;
		
		// Call the AI setup API
		this.saveAiSetup(validExamples);
	}

	saveAiSetup(goldenExamples: GoldenExample[]): void {
		// Prepare the language advice parameter
		const languageAdvice = this.languageRules.highLevelInfo || '';
		
		// Prepare the aiGoldenSet parameter as a Map
		const aiGoldenSet: { [key: string]: string } = {};
		goldenExamples.forEach((example) => {
			const key = `${example.conceptId}|${example.preferredTerm}`;
			aiGoldenSet[key] = example.translation;
		});

		// Build the API URL
		const apiUrl = `/api/${this.data.edition}/translations/${this.data.refsetId}/weblate-set/${this.data.label}/ai-setup`;
		
		// Prepare the request body
		const requestBody = {
			languageAdvice: languageAdvice,
			aiGoldenSet: aiGoldenSet
		};

		this.http.post(apiUrl, requestBody).subscribe({
			next: () => {
				this.loading = false;
				this.dialogRef.close({
					action: 'setup',
					languageRules: this.languageRules,
					goldenExamples: goldenExamples
				});
			},
			error: (error) => {
				console.error('Error saving AI setup:', error);
				this.loading = false;
				this.snackBar.open('Failed to save AI setup. Please try again.', 'Close', {
					duration: 5000
				});
			}
		});
	}
}

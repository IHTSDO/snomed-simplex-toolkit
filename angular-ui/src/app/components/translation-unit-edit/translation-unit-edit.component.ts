import {HttpClient} from '@angular/common/http';
import {ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {FormArray, FormBuilder, FormControl, Validators} from '@angular/forms';
import {ActivatedRoute, ActivatedRouteSnapshot, Router} from '@angular/router';
import {MatSnackBar} from '@angular/material/snack-bar';
import {firstValueFrom, forkJoin, merge, Observable, of, Subscription} from 'rxjs';
import {catchError, debounceTime, distinctUntilChanged, filter, finalize, map, switchMap, take, tap} from 'rxjs/operators';

/** Parses Snowstorm browser batch responses (array, or paged {@code items}). */
function snowstormBrowserBatchResponseItems(body: unknown): any[] {
	if (body == null) {
		return [];
	}
	if (Array.isArray(body)) {
		return body;
	}
	const o = body as Record<string, unknown>;
	const items = o['items'];
	if (Array.isArray(items)) {
		return items;
	}
	return [];
}
import {SimplexService} from 'src/app/services/simplex/simplex.service';
import {browserSnowstormConceptToContext, TranslationConceptContextRow} from 'src/app/utils/snowstorm-browser-concept-context';
import {TRANSLATION_STATUS_RADIO_ORDER, translationStatusRadioLabel} from 'src/app/utils/translation-status-label';

/** Snowstorm {@code POST .../concepts/partial-hierarchy} node (see PartialHierarchyNode). */
export interface PartialHierarchyNode {
	code: string;
	parents: string[] | null;
	term: string | null;
}

export interface PartialHierarchyRow {
	code: string;
	term: string;
	depth: number;
}

/**
 * Computes indent depth from parent links; list order matches Snowstorm (layer-sorted).
 */
function buildPartialHierarchyRows(nodes: PartialHierarchyNode[]): PartialHierarchyRow[] {
	if (!nodes.length) {
		return [];
	}
	const nodeMap = new Map(nodes.map((n) => [n.code, n]));
	const depthMemo = new Map<string, number>();

	function depthOf(code: string, visiting: Set<string>): number {
		if (depthMemo.has(code)) {
			return depthMemo.get(code)!;
		}
		if (visiting.has(code)) {
			return 0;
		}
		const n = nodeMap.get(code);
		if (!n) {
			return 0;
		}
		const ps = n.parents ?? [];
		let d = 0;
		visiting.add(code);
		for (const p of ps) {
			if (nodeMap.has(p)) {
				d = Math.max(d, depthOf(p, visiting) + 1);
			}
		}
		visiting.delete(code);
		depthMemo.set(code, d);
		return d;
	}

	return nodes.map((n) => ({
		code: n.code,
		term: (n.term != null && String(n.term).trim().length > 0 ? String(n.term).trim() : null) ?? n.code,
		depth: depthOf(n.code, new Set())
	}));
}

@Component({
	selector: 'app-translation-unit-edit',
	templateUrl: './translation-unit-edit.component.html',
	styleUrl: './translation-unit-edit.component.scss'
})
export class TranslationUnitEditComponent implements OnInit, OnDestroy {
	form = this.fb.group({
		primaryTerm: [''],
		status: ['NOT_STARTED', Validators.required],
		synonyms: this.fb.array<FormControl<string>>([])
	});

	edition = '';
	refset = '';
	label = '';
	conceptId = '';
	sourceTerm = '';
	/** Display name for target language (from query param `d`, set when opening from dashboard). */
	dialectDisplayName = 'Translation';
	conceptContext: TranslationConceptContextRow | null = null;
	conceptDetailsLoading = false;
	globalIndex = 0;
	pageSize = 25;
	totalCount: number | null = null;

	loading = false;
	saving = false;

	readonly statusRadioOrder = TRANSLATION_STATUS_RADIO_ORDER;
	readonly translationStatusRadioLabel = translationStatusRadioLabel;

	private sub: Subscription | null = null;
	private formSyncSub: Subscription | null = null;
	/** True while Next/Previous is navigating + loading; suppresses duplicate paramMap-driven loads. */
	private pagingNavigation = false;
	/**
	 * Snowstorm branch path from query {@code b} (e.g. MAIN or MAIN/SNOMEDCT-NO). When absent, resolved via edition API.
	 */
	private snowstormBranchQuery: string | null = null;

	/**
	 * Browser-format concept context for all concepts on the current translation page segment (see {@link globalIndex} / {@link pageSize}).
	 * Refreshed when the segment or Snowstorm branch changes; avoids one HTTP GET per concept.
	 */
	private segmentSnowstormCacheKey: string | null = null;
	private segmentConceptContextById = new Map<string, TranslationConceptContextRow>();
	/** Raw Snowstorm browser-format JSON per concept id (same segment as {@link segmentConceptContextById}). */
	private segmentRawConceptById = new Map<string, unknown>();

	/** Concept ids on the current translation page slice (e.g. up to {@link pageSize}); used to filter hierarchy rows. */
	private currentPageConceptIdSet = new Set<string>();

	/** When true, Parents/Attributes are replaced by the concept diagram. */
	showConceptDiagram = false;
	conceptDiagramUrl: string | null = null;
	conceptDiagramLoading = false;

	/** Inferred IS-A partial hierarchy for the current translation page concept ids (Snowstorm partial-hierarchy API). */
	partialHierarchyRows: PartialHierarchyRow[] = [];

	/** First target term per concept id on the current page slice (from API; live-updated for the open row from the form). */
	private sliceFirstTargetTermByConceptId = new Map<string, string>();

	/** When true, hierarchy list shows slice translations (primary term) instead of Snowstorm terms. */
	showHierarchyTranslationTerms = false;

	constructor(
		private fb: FormBuilder,
		private route: ActivatedRoute,
		private router: Router,
		private http: HttpClient,
		private simplexService: SimplexService,
		private snackBar: MatSnackBar,
		private cdr: ChangeDetectorRef
	) {}

	get synonyms(): FormArray {
		return this.form.get('synonyms') as FormArray;
	}

	/** True when preferred term and all synonyms are blank (trimmed). */
	isTranslationEmpty(): boolean {
		const primary = (this.form.get('primaryTerm')?.value as string)?.trim() ?? '';
		if (primary.length > 0) {
			return false;
		}
		for (const c of this.synonyms.controls) {
			if (((c.value ?? '') as string).trim().length > 0) {
				return false;
			}
		}
		return true;
	}

	ngOnInit(): void {
		this.formSyncSub = merge(
			this.form.get('primaryTerm')!.valueChanges,
			this.synonyms.valueChanges
		).subscribe(() => {
			this.syncStatusWithTranslationText();
			this.syncSliceMapForCurrentConceptFromForm();
		});
		this.syncStatusWithTranslationText();

		// Reload when :conceptId changes (browser back, deep links). Next/Previous also awaits load below
		// because paramMap does not always emit when the router reuses this component.
		// Map + distinct before paging filter so distinctUntilChanged tracks the new conceptId even when
		// the next emission is dropped while pagingNavigation is true (otherwise back/forward can break).
		this.sub = this.route.paramMap
			.pipe(
				map((pm) => pm.get('conceptId') ?? ''),
				distinctUntilChanged(),
				// Drop before debounce — otherwise debounce queues an emission that fires after paging ends
				// and starts a second load (loading stuck true) while firstValueFrom already completed.
				filter(() => !this.pagingNavigation),
				debounceTime(0),
				filter(() => this.isTranslationUnitEditSnapshot()),
				switchMap(() => this.loadSampleRowForSnapshot$())
			)
			.subscribe({
				next: (row) => {
					if (row == null) {
						if (this.edition && this.refset && this.label && this.conceptId) {
							this.snackBar.open('Translation row not found for this concept.', 'Dismiss', {
								duration: 6000
							});
						} else {
							this.snackBar.open('Missing route parameters.', 'Dismiss', { duration: 5000 });
						}
						return;
					}
					// Row and Snowstorm concept context are applied inside loadSampleRowForSnapshot$().
				},
				error: () => {
					this.loading = false;
					this.snackBar.open('Failed to load translation row.', 'Dismiss', { duration: 6000 });
				}
			});
	}

	private getLeafRouteSnapshot(): ActivatedRouteSnapshot {
		let n: ActivatedRouteSnapshot = this.router.routerState.snapshot.root;
		while (n.firstChild) {
			n = n.firstChild;
		}
		return n;
	}

	private isTranslationUnitEditSnapshot(): boolean {
		const p = this.getLeafRouteSnapshot().paramMap;
		return !!(
			p.get('edition') &&
			p.get('refset') &&
			p.get('label') &&
			p.get('conceptId')
		);
	}

	private applySnapshotToFields(): void {
		const q = this.router.routerState.snapshot.root.queryParamMap;
		const params = this.getLeafRouteSnapshot().paramMap;
		this.edition = params.get('edition') ?? '';
		this.refset = params.get('refset') ?? '';
		this.label = params.get('label') ?? '';
		this.conceptId = params.get('conceptId') ?? '';
		this.globalIndex = parseInt(q.get('i') ?? '0', 10);
		this.pageSize = Math.max(1, parseInt(q.get('s') ?? '25', 10));
		const dParam = q.get('d')?.trim();
		this.dialectDisplayName = dParam && dParam.length > 0 ? dParam : 'Translation';
		const t = q.get('t');
		const parsedT = t != null ? parseInt(t, 10) : NaN;
		this.totalCount = Number.isFinite(parsedT) ? parsedT : null;
		const bq = q.get('b')?.trim();
		this.snowstormBranchQuery = bq && bq.length > 0 ? bq : null;
	}

	private loadSampleRowForSnapshot$(): Observable<any | null> {
		this.applySnapshotToFields();
		if (!this.edition || !this.refset || !this.label || !this.conceptId) {
			return of(null);
		}
		this.loading = true;
		const sample$ =
			this.totalCount == null
				? this.simplexService.getTranslationSetRows(this.edition, this.refset, this.label, 0, 1).pipe(
						switchMap((page0) => {
							this.totalCount = page0.count ?? 0;
							const results = page0.results ?? [];
							const first = results[0];
							if (first != null && String(first.context) === String(this.conceptId)) {
								return of(first);
							}
							return this.simplexService.getTranslationSetSampleRow(
								this.edition,
								this.refset,
								this.label,
								this.conceptId
							);
						})
					)
				: this.simplexService.getTranslationSetSampleRow(
						this.edition,
						this.refset,
						this.label,
						this.conceptId
					);

		return sample$.pipe(
			tap({
				error: () => {
					this.loading = false;
				}
			}),
			switchMap((row) => this.loadRowIntoEditor$(row)),
			finalize(() => {
				this.loading = false;
			})
		);
	}

	/** Applies API row data to the form and loads Snowstorm browser context (shared by snapshot load and next/prev paging). */
	private loadRowIntoEditor$(
		row: any | null,
		snowstormPrefetch?: { page: number; pageSize: number; results: any[] }
	): Observable<any | null> {
		this.loading = false;
		if (row == null) {
			this.partialHierarchyRows = [];
			this.currentPageConceptIdSet.clear();
			this.sliceFirstTargetTermByConceptId.clear();
			return of(null);
		}
		this.resetConceptDiagramUi();
		this.applyRow(row);
		this.conceptDetailsLoading = true;
		this.conceptContext = null;
		return this.loadSnowstormConceptContext$(snowstormPrefetch).pipe(
			tap((ctx) => {
				this.conceptContext = ctx;
			}),
			catchError(() => of(null)),
			finalize(() => {
				this.conceptDetailsLoading = false;
			}),
			map(() => row)
		);
	}

	private segmentCacheKeyForBranch(branch: string): string {
		const page = Math.floor(this.globalIndex / this.pageSize);
		return `${this.edition}|${this.refset}|${this.label}|${branch}|${page}|${this.pageSize}`;
	}

	private fillSliceTranslationTermsFromResults(results: any[]): void {
		this.sliceFirstTargetTermByConceptId.clear();
		for (const r of results) {
			const cid = r?.context != null ? String(r.context) : '';
			if (!cid) {
				continue;
			}
			const target: unknown[] = Array.isArray(r.target) ? r.target : [];
			const first = (target[0] != null ? String(target[0]) : '').trim();
			this.sliceFirstTargetTermByConceptId.set(cid, first);
		}
	}

	/** Keeps the open row’s slice map entry aligned with the preferred term field (while editing). */
	private syncSliceMapForCurrentConceptFromForm(): void {
		if (!this.conceptId) {
			return;
		}
		const primary = (this.form.get('primaryTerm')?.value as string)?.trim() ?? '';
		this.sliceFirstTargetTermByConceptId.set(this.conceptId, primary);
	}

	/** Hierarchy label when {@link showHierarchyTranslationTerms} is on (translation or fallback SNOMED term). */
	hierarchyTermDisplay(row: PartialHierarchyRow): string {
		if (!this.showHierarchyTranslationTerms) {
			return row.term;
		}
		const t = (this.sliceFirstTargetTermByConceptId.get(row.code) ?? '').trim();
		return t.length > 0 ? t : row.term;
	}

	/** True when showing translations but this concept has no primary target yet. */
	hierarchyTermIsMissingTranslation(row: PartialHierarchyRow): boolean {
		if (!this.showHierarchyTranslationTerms) {
			return false;
		}
		return (this.sliceFirstTargetTermByConceptId.get(row.code) ?? '').trim().length === 0;
	}

	private fillSegmentConceptCacheFromRaw(rawConcepts: any[]): void {
		this.segmentConceptContextById.clear();
		this.segmentRawConceptById.clear();
		for (const raw of rawConcepts) {
			const ctx = browserSnowstormConceptToContext(raw);
			if (ctx) {
				this.segmentConceptContextById.set(ctx.conceptId, ctx);
			}
			const cid = raw?.conceptId != null ? String(raw.conceptId) : '';
			if (cid.length > 0) {
				this.segmentRawConceptById.set(cid, raw);
			}
		}
	}

	/**
	 * Loads browser-format SNOMED concepts for the current translation page in one request
	 * ({@code POST .../browser/{branch}/concepts/bulk-load} with {@code conceptIds}).
	 */
	private ensureSegmentSnowstormCache$(
		branch: string,
		prefetch?: { page: number; pageSize: number; results: any[] }
	): Observable<void> {
		const page = Math.floor(this.globalIndex / this.pageSize);
		const segmentKey = this.segmentCacheKeyForBranch(branch);
		if (
			this.segmentSnowstormCacheKey === segmentKey &&
			this.segmentConceptContextById.has(this.conceptId)
		) {
			return of(undefined);
		}

		let results$: Observable<any[]>;
		if (
			prefetch != null &&
			prefetch.page === page &&
			prefetch.pageSize === this.pageSize &&
			Array.isArray(prefetch.results)
		) {
			results$ = of(prefetch.results);
		} else {
			results$ = this.simplexService
				.getTranslationSetRows(this.edition, this.refset, this.label, page, this.pageSize)
				.pipe(map((resp) => resp.results ?? []));
		}

		return results$.pipe(
			switchMap((results) => {
				const ids = [
					...new Set(
						results
							.map((r) => (r?.context != null ? String(r.context) : ''))
							.filter((id) => id.length > 0)
					)
				];
				if (ids.length === 0) {
					this.segmentConceptContextById.clear();
					this.segmentRawConceptById.clear();
					this.segmentSnowstormCacheKey = segmentKey;
					this.partialHierarchyRows = [];
					this.currentPageConceptIdSet.clear();
					this.sliceFirstTargetTermByConceptId.clear();
					return of(undefined);
				}
				this.currentPageConceptIdSet = new Set(ids);
				const body = { conceptIds: ids };
				const enc = encodeURIComponent(branch);
				const urlBulk = `/snowstorm/snomed-ct/browser/${enc}/concepts/bulk-load`;
				const urlPartial = `/snowstorm/snomed-ct/browser/${enc}/concepts/partial-hierarchy`;
				const partialBody = { codes: ids, includeTerms: true };
				this.partialHierarchyRows = [];
				return forkJoin({
					bulk: this.http.post<unknown>(urlBulk, body),
					partial: this.http.post<PartialHierarchyNode[]>(urlPartial, partialBody).pipe(
						catchError((err) => {
							console.warn('Partial SNOMED hierarchy unavailable (Snowstorm 10.7+):', err);
							return of<PartialHierarchyNode[]>([]);
						})
					)
				}).pipe(
					tap(({ bulk, partial }) => {
						const items = Array.isArray(bulk) ? bulk : snowstormBrowserBatchResponseItems(bulk);
						this.fillSegmentConceptCacheFromRaw(items);
						this.segmentSnowstormCacheKey = segmentKey;
						this.fillSliceTranslationTermsFromResults(results);
						this.syncSliceMapForCurrentConceptFromForm();
						const sliceNodes = partial.filter((n) => this.currentPageConceptIdSet.has(n.code));
						const rows = buildPartialHierarchyRows(sliceNodes);
						const orderInPage = new Map<string, number>();
						results.forEach((r, i) => {
							const cid = r?.context != null ? String(r.context) : '';
							if (cid.length > 0 && !orderInPage.has(cid)) {
								orderInPage.set(cid, i);
							}
						});
						rows.sort((a, b) => (orderInPage.get(a.code) ?? 0) - (orderInPage.get(b.code) ?? 0));
						this.partialHierarchyRows = rows;
					}),
					switchMap(() =>
						this.segmentConceptContextById.has(this.conceptId)
							? of(undefined)
							: this.fetchSingleSnowstormConcept$(branch)
					),
					catchError(() => this.fetchSingleSnowstormConcept$(branch))
				);
			})
		);
	}

	private fetchSingleSnowstormConcept$(branch: string): Observable<void> {
		return this.http
			.get<unknown>(
				`/snowstorm/snomed-ct/browser/${encodeURIComponent(branch)}/concepts/${encodeURIComponent(this.conceptId)}`
			)
			.pipe(
				tap((raw) => {
					const ctx = browserSnowstormConceptToContext(raw);
					if (ctx) {
						this.segmentConceptContextById.set(ctx.conceptId, ctx);
					}
					const cid = (raw as { conceptId?: unknown })?.conceptId;
					if (cid != null) {
						this.segmentRawConceptById.set(String(cid), raw);
					}
				}),
				map(() => undefined),
				catchError(() => {
					this.snackBar.open(
						'Could not load concept details from the terminology server.',
						'Dismiss',
						{ duration: 6000 }
					);
					return of(undefined);
				})
			);
	}

	private loadSnowstormConceptContext$(
		prefetch?: { page: number; pageSize: number; results: any[] }
	): Observable<TranslationConceptContextRow | null> {
		const branch$ =
			this.snowstormBranchQuery != null && this.snowstormBranchQuery.trim().length > 0
				? of(this.snowstormBranchQuery.trim())
				: this.simplexService.getEdition(this.edition).pipe(
						map((ed: any) => {
							const b = ed?.simplexWorkingBranch || ed?.branchPath;
							return b && String(b).trim() ? String(b).trim() : 'MAIN';
						}),
						catchError(() => of('MAIN')),
						take(1)
					);
		return branch$.pipe(
			switchMap((branch) =>
				this.ensureSegmentSnowstormCache$(branch, prefetch).pipe(
					map(() => this.segmentConceptContextById.get(this.conceptId) ?? null)
				)
			)
		);
	}

	ngOnDestroy(): void {
		this.sub?.unsubscribe();
		this.formSyncSub?.unsubscribe();
		this.revokeConceptDiagramUrl();
	}

	private resetConceptDiagramUi(): void {
		this.showConceptDiagram = false;
		this.conceptDiagramLoading = false;
		this.revokeConceptDiagramUrl();
	}

	private revokeConceptDiagramUrl(): void {
		if (this.conceptDiagramUrl) {
			URL.revokeObjectURL(this.conceptDiagramUrl);
			this.conceptDiagramUrl = null;
		}
	}

	onConceptDiagramToggle(checked: boolean): void {
		if (!checked) {
			this.showConceptDiagram = false;
			this.revokeConceptDiagramUrl();
			return;
		}
		const raw = this.segmentRawConceptById.get(this.conceptId);
		if (raw == null) {
			this.snackBar.open('Concept details are not available for the diagram.', 'Dismiss', {
				duration: 6000
			});
			return;
		}
		this.showConceptDiagram = true;
		this.conceptDiagramLoading = true;
		this.http
			.post(`api/codesystems/${encodeURIComponent(this.edition)}/diagrams/preview`, raw, {
				responseType: 'blob'
			})
			.pipe(
				finalize(() => {
					this.conceptDiagramLoading = false;
					this.cdr.markForCheck();
				})
			)
			.subscribe({
				next: (blob) => {
					this.revokeConceptDiagramUrl();
					this.conceptDiagramUrl = URL.createObjectURL(blob);
				},
				error: () => {
					this.showConceptDiagram = false;
					this.snackBar.open('Could not load concept diagram.', 'Dismiss', { duration: 6000 });
				}
			});
	}

	/**
	 * Empty translation ⇒ status {@code NOT_STARTED} and disabled control.
	 * With terms ⇒ enabled; if status was {@code NOT_STARTED}, default to {@code FOR_REVIEW}.
	 */
	private syncStatusWithTranslationText(): void {
		const statusCtrl = this.form.get('status');
		if (!statusCtrl) {
			return;
		}
		const empty = this.isTranslationEmpty();
		if (empty) {
			statusCtrl.patchValue('NOT_STARTED', { emitEvent: false });
			if (statusCtrl.enabled) {
				statusCtrl.disable({ emitEvent: false });
			}
		} else {
			if (statusCtrl.disabled) {
				statusCtrl.enable({ emitEvent: false });
			}
			if (statusCtrl.value === 'NOT_STARTED') {
				statusCtrl.patchValue('FOR_REVIEW', { emitEvent: false });
			}
		}
	}

	private applyRow(row: any): void {
		this.sourceTerm = row.source?.[0] ?? '';
		const target: string[] = Array.isArray(row.target) ? row.target : [];
		const primary = target[0] ?? '';
		while (this.synonyms.length) {
			this.synonyms.removeAt(0);
		}
		for (let i = 1; i < target.length; i++) {
			this.synonyms.push(this.fb.control(target[i] ?? '', { nonNullable: true }));
		}
		const status = row.status ?? 'NOT_STARTED';
		this.form.patchValue({
			primaryTerm: primary,
			status
		});
		this.syncStatusWithTranslationText();
		this.syncSliceMapForCurrentConceptFromForm();
	}

	addSynonym(): void {
		this.synonyms.push(this.fb.control('', { nonNullable: true }));
	}

	removeSynonym(index: number): void {
		this.synonyms.removeAt(index);
	}

	/** Swap this synonym’s text with the Preferred Term (primary) field. */
	makeSynonymPreferredTerm(index: number): void {
		const primaryCtrl = this.form.get('primaryTerm');
		const synCtrl = this.synonyms.at(index);
		if (!primaryCtrl || !synCtrl) {
			return;
		}
		const pt = (primaryCtrl.value as string) ?? '';
		const syn = (synCtrl.value as string) ?? '';
		primaryCtrl.setValue(syn);
		synCtrl.setValue(pt);
	}

	descriptionRoleLabel(role: string): string {
		switch (role) {
			case 'FSN':
				return 'FSN';
			case 'PT':
				return 'Preferred term';
			case 'SYNONYM':
				return 'Synonym';
			default:
				return role;
		}
	}

	snomedConceptUri(snomedConceptId: string): string {
		return `https://snomed.info/id/${snomedConceptId}`;
	}

	goBack(): void {
		this.router.navigate([
			'/translation-studio',
			this.edition,
			this.refset,
			this.label
		]);
	}

	canGoPrev(): boolean {
		return this.globalIndex > 0;
	}

	canGoNext(): boolean {
		return this.totalCount != null && this.globalIndex < this.totalCount - 1;
	}

	/** Move to adjacent concept without persisting the form (contrast: Save and Previous / Save and Next). */
	goPrevWithoutSave(): void {
		if (this.canGoPrev() && !this.loading && !this.saving) {
			void this.navigateToGlobalIndex(this.globalIndex - 1);
		}
	}

	goNextWithoutSave(): void {
		if (this.canGoNext() && !this.loading && !this.saving) {
			void this.navigateToGlobalIndex(this.globalIndex + 1);
		}
	}

	async saveAndPrev(): Promise<void> {
		await this.saveAndNavigate(-1);
	}

	async saveAndNext(): Promise<void> {
		await this.saveAndNavigate(1);
	}

	private async saveAndNavigate(delta: number): Promise<void> {
		const nextIndex = this.globalIndex + delta;
		if (delta < 0 && !this.canGoPrev()) {
			return;
		}
		if (delta > 0 && !this.canGoNext()) {
			return;
		}
		if (this.saving || this.loading) {
			return;
		}
		this.saving = true;
		try {
			await firstValueFrom(this.saveUnit$());
			this.snackBar.open('Saved.', 'Dismiss', { duration: 3000 });
			await this.navigateToGlobalIndex(nextIndex);
		} catch {
			// Errors surfaced by SimplexService.handleError
		} finally {
			this.saving = false;
		}
	}

	private async navigateToGlobalIndex(newIndex: number): Promise<void> {
		const s = this.pageSize;
		const page = Math.floor(newIndex / s);
		const idx = newIndex % s;
		if (!this.edition || !this.refset || !this.label) {
			return;
		}
		this.pagingNavigation = true;
		this.loading = true;
		try {
			const pageResp = await firstValueFrom(
				this.simplexService.getTranslationSetRows(this.edition, this.refset, this.label, page, s)
			);
			const results = pageResp.results ?? [];
			const listRow = results[idx];
			const nextConceptId = listRow?.context != null ? String(listRow.context) : '';
			if (!nextConceptId) {
				this.loading = false;
				this.snackBar.open('Could not load adjacent row.', 'Dismiss', { duration: 5000 });
				return;
			}
			const t = this.totalCount ?? pageResp.count ?? 0;
			const queryParams: Record<string, string | number> = {
				i: newIndex,
				s,
				t
			};
			if (this.dialectDisplayName !== 'Translation') {
				queryParams['d'] = this.dialectDisplayName;
			}
			if (this.snowstormBranchQuery) {
				queryParams['b'] = this.snowstormBranchQuery;
			}
			const urlTree = this.router.createUrlTree(
				['/translation-studio', this.edition, this.refset, this.label, 'edit', nextConceptId],
				{ queryParams }
			);
			const ok = await this.router.navigateByUrl(urlTree);
			if (!ok) {
				this.loading = false;
				this.snackBar.open('Navigation was blocked; try again.', 'Dismiss', { duration: 5000 });
				return;
			}
			// Route reuse: paramMap may not emit; apply row from the page we already fetched (same data as GET .../unit/...).
			this.applySnapshotToFields();
			await firstValueFrom(this.loadRowIntoEditor$(listRow, { page, pageSize: s, results }).pipe(take(1)));
			this.loading = false;
			this.cdr.detectChanges();
		} catch {
			this.loading = false;
			this.snackBar.open('Failed to page through translation set.', 'Dismiss', {
				duration: 5000
			});
		} finally {
			this.pagingNavigation = false;
		}
	}

	private buildUpdateBody(): { terms: string[]; status: string } {
		const primary = (this.form.get('primaryTerm')?.value as string)?.trim() ?? '';
		const synVals = this.synonyms.controls
			.map((c) => (c.value as string)?.trim() ?? '')
			.filter((x) => x.length > 0);
		const terms = primary ? [primary, ...synVals] : synVals;
		const status = this.isTranslationEmpty()
			? 'NOT_STARTED'
			: ((this.form.getRawValue().status as string) ?? 'FOR_REVIEW');
		return { terms, status };
	}

	private saveUnit$(): Observable<unknown> {
		return this.simplexService.updateTranslationUnit(
			this.edition,
			this.refset,
			this.label,
			this.conceptId,
			this.buildUpdateBody()
		);
	}

	save(): void {
		this.saving = true;
		this.saveUnit$().subscribe({
			next: () => {
				this.saving = false;
				this.snackBar.open('Saved.', 'Dismiss', { duration: 3000 });
			},
			error: () => {
				this.saving = false;
			}
		});
	}
}

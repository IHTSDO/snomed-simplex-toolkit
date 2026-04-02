import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, of, throwError } from 'rxjs';
import { catchError, delay, shareReplay, tap } from 'rxjs/operators';
import { UiConfigurationService } from '../ui-configuration/ui-configuration.service';

@Injectable({
  providedIn: 'root'
})
export class SimplexService {

  private editionsList$: Observable<any> | null = null;
  private editionDetailCache = new Map<string, Observable<any>>();
  private translationsListCache = new Map<string, Observable<any>>();

  constructor(private http: HttpClient, private snackBar: MatSnackBar, private uiConfigurationService: UiConfigurationService) { }

  private handleError(error: HttpErrorResponse) {
    if (error.status === 403) {
      this.login();
    } else if (error.status === 504) {
      this.snackBar.open(`Error: Can't contact simplex server`, 'Dismiss', {
        duration: 5000
      });
    } else {
      this.snackBar.open(`Error: ${error.error.message}`, 'Dismiss', {
        duration: 15000
      });
    }
    // Return an observable with a user-facing error message
    console.error(error)
    return throwError(error);
  }

  public login(): void {
    let config: any = this.uiConfigurationService.getConfiguration();
    const redirectUrl = `${config.endpoints.imsEndpoint}login?serviceReferer=${window.location.href}`;
    window.location.href = redirectUrl;
  }

  public logout(): void {
    let config: any = this.uiConfigurationService.getConfiguration();
    const redirectUrl = `${config.endpoints.imsEndpoint}logout?serviceReferer=${window.location.href}`;
    window.location.href = redirectUrl;
  }

  public logoutAccount(): Observable<any> {
    let config: any = this.uiConfigurationService.getConfiguration();
    let endpoint: string = config.endpoints.imsEndpoint;
    // endpoint = endpoint.replace('#/', '');
    const url = `${endpoint}api/account/logout`;
    return this.http.post(url, {}).pipe(catchError(this.handleError.bind(this)));
  }

  public refreshUIConfiguration(): void {
    this.http.get('api/ui-configuration').subscribe((config) => {
      this.uiConfigurationService.setConfiguration(config);
    });
  }

  public getEditions(): Observable<any> {
    if (!this.editionsList$) {
      this.editionsList$ = this.http.get('api/codesystems').pipe(
        catchError((err) => {
          this.editionsList$ = null;
          return this.handleError(err);
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.editionsList$;
  }

  public getEdition(edition: string): Observable<any> {
    let cached = this.editionDetailCache.get(edition);
    if (!cached) {
      cached = this.http.get(`api/codesystems/${edition}`).pipe(
        catchError((err) => {
          this.editionDetailCache.delete(edition);
          return this.handleError(err);
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );
      this.editionDetailCache.set(edition, cached);
    }
    return cached;
  }

  private clearEditionsListCache(): void {
    this.editionsList$ = null;
  }

  private clearEditionDetailCache(editionShortName: string): void {
    this.editionDetailCache.delete(editionShortName);
  }

  private clearTranslationsCache(editionShortName: string): void {
    this.translationsListCache.delete(editionShortName);
  }

  public getCodeSystemForBranch(branch: string): Observable<any> {
    return this.http.get(`/snowstorm/snomed-ct/codesystems?forBranch=${branch}`).pipe(catchError(this.handleError.bind(this)));
  }

  public getCodeSystemVersions(codeSystem: string): Observable<any> {
    return this.http.get(`/snowstorm/snomed-ct/codesystems/${codeSystem}/versions`).pipe(catchError(this.handleError.bind(this)));
  }

  public getSimpleRefsets(edition: string): Observable<any> {
    return this.http.get(`api/${edition}/refsets/simple`).pipe(catchError(this.handleError.bind(this)));
  }

  public getSimpleMaps(edition: string): Observable<any> {
    return this.http.get(`api/${edition}/refsets/simple-map-to-snomed-with-correlation`).pipe(catchError(this.handleError.bind(this)));
  }

  public getTranslations(edition: string): Observable<any> {
    let cached = this.translationsListCache.get(edition);
    if (!cached) {
      cached = this.http.get(`api/${edition}/translations`).pipe(
        catchError((err) => {
          this.translationsListCache.delete(edition);
          return this.handleError(err);
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );
      this.translationsListCache.set(edition, cached);
    }
    return cached;
  }

  public createEdition(edition: any): Observable<any> {
    return this.http.post('api/codesystems', edition).pipe(
      tap(() => this.clearEditionsListCache()),
      catchError(this.handleError.bind(this)));
  }

  public createSimpleRefset(edition: string, simpleRefset: any): Observable<any> {
    return this.http.post(`api/${edition}/refsets/simple`, simpleRefset).pipe(catchError(this.handleError.bind(this)));
  }

  public createMap(edition: string, map: any): Observable<any> {
    return this.http.post(`api/${edition}/refsets/simple-map-to-snomed-with-correlation`, map).pipe(catchError(this.handleError.bind(this)));
  }

  public createTranslations(edition: string, translation: any): Observable<any> {
    return this.http.post(`api/${edition}/translations`, translation).pipe(
      tap(() => this.clearTranslationsCache(edition)),
      catchError(this.handleError.bind(this)));
  }

  public showCustomConcepts(edition: string): Observable<any> {
    return this.http.post(`api/${edition}/concepts/show`, {}).pipe(
      tap(() => this.clearEditionDetailCache(edition)),
      catchError(this.handleError.bind(this)));
  }

  public hideCustomConcepts(edition: string): Observable<any> {
    return this.http.post(`api/${edition}/concepts/hide`, {}).pipe(
      tap(() => this.clearEditionDetailCache(edition)),
      catchError(this.handleError.bind(this)));
  }

  public deleteEdition(edition: string): Observable<any> {
    return this.http.delete(`api/codesystems/${edition}`).pipe(
      tap(() => {
        this.clearEditionsListCache();
        this.clearEditionDetailCache(edition);
        this.clearTranslationsCache(edition);
      }),
      catchError(this.handleError.bind(this)));
  }

  public uploadRefsetToolTranslation(edition: string, refsetId: string, file: File): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);
    const apiUrl = `api/${edition}/translations/${refsetId}/refset-tool`;
    return this.http.put(apiUrl, formData).pipe(
      tap(() => this.clearTranslationsCache(edition)),
      catchError(this.handleError.bind(this)));
  }

  public uploadTranslationCsv(edition: string, refsetId: string, file: File): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);
    const apiUrl = `api/${edition}/translations/${refsetId}/translation-csv`;
    return this.http.put(apiUrl, formData).pipe(
      tap(() => this.clearTranslationsCache(edition)),
      catchError(this.handleError.bind(this)));
  }

  public uploadSpreadsheetRefset(edition: string, refsetId: string, file: File): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);
    const apiUrl = `api/${edition}/refsets/simple/${refsetId}/spreadsheet`;
    return this.http.put(apiUrl, formData).pipe(catchError(this.handleError.bind(this)));
  }

  public uploadRefsetToolSubset(edition: string, refsetId: string, file: File): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);
    const apiUrl = `api/${edition}/refsets/simple/${refsetId}/refset-tool`;
    return this.http.put(apiUrl, formData).pipe(catchError(this.handleError.bind(this)));
  }

  public uploadSpreadsheetMap(edition: string, refsetId: string, file: File): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);
    const apiUrl = `api/${edition}/refsets/simple-map-to-snomed-with-correlation/${refsetId}/spreadsheet`;
    return this.http.put(apiUrl, formData).pipe(catchError(this.handleError.bind(this)));
  }

  public uploadConceptsSpreadsheet(edition: string, file: File): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);
    const apiUrl = `api/${edition}/concepts/spreadsheet`;
    return this.http.put(apiUrl, formData).pipe(catchError(this.handleError.bind(this)));
  }

  // New download method
  public downloadConceptsSpreadsheet(edition: string): Observable<Blob> {
    const apiUrl = `api/${edition}/concepts/spreadsheet`;
    return this.http.get(apiUrl, { responseType: 'blob' }).pipe(
      catchError(this.handleError.bind(this))
    );
  }

  public downloadRefsetSpreadsheet(edition: string, refsetId: string): Observable<Blob> {
    const apiUrl = `api/${edition}/refsets/simple/${refsetId}/spreadsheet`;
    return this.http.get(apiUrl, { responseType: 'blob' }).pipe(
      catchError(this.handleError.bind(this))
    );
  }

  // Method to trigger file download in the browser
  public triggerDownload(file: Blob, filename: string): void {
    const url = window.URL.createObjectURL(file);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    window.URL.revokeObjectURL(url);
  }

  public getConcepts(edition: string, offset?: number, limit?: number): Observable<any> {
    if (!offset) offset = 0;
    if (!limit) limit = 100;
    const url = `api/${edition}/concepts?offset=${offset}&limit=${limit}`;
    return this.http.get(url).pipe(catchError(this.handleError.bind(this)));
  }

  public startClassification(edition: string): Observable<any> {
    return this.http.post(`api/codesystems/${edition}/classify`, null).pipe(catchError(this.handleError.bind(this)));
  }

  public getJobs(edition: string, filter?: string): Observable<any> {
    if (!filter) {
      return this.http.get(`api/${edition}/jobs`).pipe(catchError(this.handleError.bind(this)));
    } else if (filter === 'external') {
      return this.http.get(`api/${edition}/jobs?jobType=EXTERNAL_SERVICE`).pipe(catchError(this.handleError.bind(this)));
    } else if (filter === 'concepts') {
      return this.http.get(`api/${edition}/jobs?jobType=CONCEPT_CHANGE`).pipe(catchError(this.handleError.bind(this)));
    } else {
      return this.http.get(`api/${edition}/jobs?refsetId=${filter}`).pipe(catchError(this.handleError.bind(this)));
    }
  }

  public getActivities(edition: string, offset?: number, limit?: number, componentId?: string): Observable<any> {
    if (!offset) offset = 0;
    if (!limit) limit = 20;
    let url = `api/${edition}/activities?offset=${offset}&limit=${limit}`;
    if (componentId) {
      url += `&componentId=${encodeURIComponent(componentId)}`;
    }
    return this.http.get(url).pipe(catchError(this.handleError.bind(this)));
  }

  public linkTranslationToSnolate(edition: string, refsetId: string): Observable<any> {
    return this.http.post(`api/${edition}/translations/${refsetId}/snolate-setup`, {}).pipe(
      tap(() => this.clearTranslationsCache(edition)),
      catchError(this.handleError.bind(this)));
  }

  public getRoles(): Observable<any> {
    return this.http.get('api/auth').pipe(catchError(this.handleError.bind(this)));
  }

  public startValidation(edition: string): Observable<any> {
    return this.http.post(`api/codesystems/${edition}/validate`, null).pipe(catchError(this.handleError.bind(this)));
  }

  public getValidationResultSpreadsheet(edition: string): Observable<any> {
    const apiUrl = `api/codesystems/${edition}/validate/spreadsheet`;
    return this.http.get(apiUrl, { responseType: 'blob' }).pipe(
      catchError(this.handleError.bind(this))
    );
  }

  public getValidationResults(edition: string): Observable<any> {
    return this.http.get(`api/codesystems/${edition}/validate/issues`).pipe(catchError(this.handleError.bind(this)));
  }


  // Release workflow mangement //

  public startAuthoring(edition: string): Observable<any> {
    return this.http.post(`api/codesystems/${edition}/start-authoring`, null).pipe(catchError(this.handleError.bind(this)));
  }

  public startReleasePreparation(edition: string): Observable<any> {
    return this.http.post(`api/codesystems/${edition}/start-release-prep`, null).pipe(catchError(this.handleError.bind(this)));
  }

  public approveContentForRelease(edition: string): Observable<any> {
    return this.http.post(`api/codesystems/${edition}/approve-content-for-release`, null).pipe(catchError(this.handleError.bind(this)));
  }

  public createReleaseCandidate(edition: string): Observable<any> {
    return this.http.post(`api/codesystems/${edition}/create-release-candidate`, null).pipe(catchError(this.handleError.bind(this)));
  }

  public finalizeRelease(edition: string): Observable<any> {
    return this.http.post(`api/codesystems/${edition}/finalize-release`, null).pipe(catchError(this.handleError.bind(this)));
  }

  public startMaintenance(edition: string): Observable<any> {
    return this.http.post(`api/codesystems/${edition}/start-maintenance`, null).pipe(catchError(this.handleError.bind(this)));
  }

  // ---------------------------- //

  getProductPackagingConfiguration(edition: string): Observable<any> {
    return this.http.get(`api/codesystems/${edition}/product-packaging/configuration`).pipe(catchError(this.handleError.bind(this)));
  }

  updateProductPackagingConfiguration(edition: string, configuration: any): Observable<any> {
    return this.http.put(`api/codesystems/${edition}/product-packaging/configuration`, configuration).pipe(catchError(this.handleError.bind(this)));
  }

  upgradeEdition(edition: string, newDependantVersion: number): Observable<any> {
    const data = {
      "newDependantVersion": newDependantVersion,
      "contentAutomations": true
    }
    return this.http.post(`api/codesystems/${edition}/upgrade`, data).pipe(
      tap(() => {
        this.clearEditionsListCache();
        this.clearEditionDetailCache(edition);
        this.clearTranslationsCache(edition);
      }),
      catchError(this.handleError.bind(this)));
  }

  public getReleaseCandidatePackage(edition: string): Observable<Blob> {
    const apiUrl = `api/codesystems/${edition}/release-candidate`;
    return this.http.get(apiUrl, { responseType: 'blob' }).pipe(
      catchError(this.handleError.bind(this))
    );
  }

  getReleasePackages(edition: string): Observable<any> {
    return this.http.get(`api/codesystems/${edition}/versions`).pipe(catchError(this.handleError.bind(this)));
  }

  public getReleasePackage(edition: string, version: string): Observable<Blob> {
    const apiUrl = `api/codesystems/${edition}/versions/${version}/package`;
    return this.http.get(apiUrl, { responseType: 'blob' }).pipe(
      catchError(this.handleError.bind(this))
    );
  }

  public getLanguageCodes(): Observable<any> {
    return this.http.get(`api/language-codes`).pipe(catchError(this.handleError.bind(this)));
  }

  // Snolate translation set utilities

  public getLabelSets(edition: string): Observable<any> {
    const mockLabelSets = [
      { id: '1', name: 'Top 1000 clinical findings', length: 1000 },
      { id: '2', name: 'Nursing procedures', length: 145 },
      { id: '3', name: 'Substances for allergies', length: 200 }
    ];
    return of(mockLabelSets).pipe(delay(1000)); // 1 second delay

    // return this.http.get(`api/${edition}/label-sets`).pipe(catchError(this.handleError.bind(this)));
  }

  public getLabelSetDetails(edition: string, labelSetId: string): Observable<any> {
    return this.http.get(`api/${edition}/label-sets/${labelSetId}`).pipe(catchError(this.handleError.bind(this)));
  }

  getLabelSetMembers(edition: string, selectedTranslation: string, labelSetId: string): Observable<any> {
    return this.http.get(`api/${edition}/translations/${selectedTranslation}/snolate-set/${labelSetId}/sample-rows`).pipe(catchError(this.handleError.bind(this)));
  }

  public getTranslationSets(edition: string, refsetId: string): Observable<any> {
    return this.http.get(`api/${edition}/translations/${refsetId}/snolate-set`).pipe(catchError(this.handleError.bind(this)));
  }

  public getAllTranslationSets(edition: string): Observable<any> {
    return this.http.get(`api/${edition}/translations/snolate-set`).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteTranslationSet(edition: string, refsetId: string, label: string): Observable<any> {
    return this.http.delete(`api/${edition}/translations/${refsetId}/snolate-set/${label}`).pipe(catchError(this.handleError.bind(this)));
  }

  public createTranslationSet(edition: string, refsetId: string, translationSetData: any): Observable<any> {
    return this.http.post(`api/${edition}/translations/${refsetId}/snolate-set`, translationSetData).pipe(catchError(this.handleError.bind(this)));
  }

  public pullFromSnolate(edition: string, refsetId: string, label: string, apTaskRequest?: any): Observable<any> {
    return this.http.post(`api/${edition}/translations/${refsetId}/snolate-set/${label}/pull-content`, apTaskRequest || {}).pipe(catchError(this.handleError.bind(this)));
  }

  public refreshSnolateSet(edition: string, refsetId: string, label: string): Observable<any> {
    return this.http.post(`api/${edition}/translations/${refsetId}/snolate-set/${label}/refresh`, {}).pipe(catchError(this.handleError.bind(this)));
  }

  public getTranslationSetDetails(edition: string, refsetId: string, label: string): Observable<any> {
    return this.http.get(`api/${edition}/translations/${refsetId}/snolate-set/${label}`).pipe(catchError(this.handleError.bind(this)));
  }

  public runAiBatchTranslation(edition: string, refsetId: string, label: string, batchSize: number): Observable<any> {
    const request = {
      size: batchSize
    };
    return this.http.post(`api/${edition}/translations/${refsetId}/snolate-set/${label}/run-ai-batch`, request)
      .pipe(catchError(this.handleError.bind(this)));
  }

  public getCurrentAPTask(edition: string, refsetId: string, label: string): Observable<any> {
    return this.http.get(`api/${edition}/translations/current-ap-task`)
  }

  public pollForExport(edition: string, id: string): Observable<any> {
    return this.http.get(`api/${edition}/jobs/${id}`)
  }

}

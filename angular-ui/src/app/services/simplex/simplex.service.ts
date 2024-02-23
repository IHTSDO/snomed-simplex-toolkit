import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, lastValueFrom, of, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { UiConfigurationService } from '../ui-configuration/ui-configuration.service';

@Injectable({
  providedIn: 'root'
})
export class SimplexService {

  constructor(private http: HttpClient, private snackBar: MatSnackBar, private uiConfigurationService: UiConfigurationService) { }

  private handleError(error: HttpErrorResponse) {
    if (error.status === 403) {
      this.login();
    } else if (error.status === 504) {
      this.snackBar.open(`Error: Can't contact simplex server`, 'Dismiss', {
        duration: 5000
      });
    }  else {
      this.snackBar.open(`Error: ${ error.error.message}`, 'Dismiss', {
        duration: 15000
      });
    } 
    // Return an observable with a user-facing error message
    console.error(error)
    return throwError(error);
  }

  public login(): void{
    let config: any = this.uiConfigurationService.getConfiguration();
    const redirectUrl = `${config.endpoints.imsEndpoint}login?serviceReferer=${window.location.href}`;
    window.location.href = redirectUrl;
  }

  public logout(): void{
    let config: any = this.uiConfigurationService.getConfiguration();
    const redirectUrl = `${config.endpoints.imsEndpoint}logout?serviceReferer=${window.location.href}`;
    window.location.href = redirectUrl;
  }

  public refreshUIConfiguration(): void {
    this.http.get('/api/ui-configuration').subscribe((config) => {
      this.uiConfigurationService.setConfiguration(config);
    });
  }

  public getEditions(): Observable<any> {
    return this.http.get('/api/codesystems').pipe(catchError(this.handleError.bind(this)));
  }

  public getEdition(edition: string): Observable<any> {
    return this.http.get(`/api/codesystems/${edition}`).pipe(catchError(this.handleError.bind(this)));
  }

  public getSimpleRefsets(edition: string): Observable<any> {
    return this.http.get(`/api/${edition}/refsets/simple`).pipe(catchError(this.handleError.bind(this)));
  }

  public getSimpleMaps(edition: string): Observable<any> {
    return this.http.get(`/api/${edition}/refsets/simple-map-to-snomed-with-correlation`).pipe(catchError(this.handleError.bind(this)));
  }

  public getTranslations(edition: string): Observable<any> {
    return this.http.get(`/api/${edition}/translations`).pipe(catchError(this.handleError.bind(this)));
  }

  public createEdition(edition: any): Observable<any> {
    return this.http.post('/api/codesystems', edition).pipe(catchError(this.handleError.bind(this)));
  }

  public createSimpleRefset(edition: string, simpleRefset: any): Observable<any> {
    return this.http.post(`/api/${edition}/refsets/simple`, simpleRefset).pipe(catchError(this.handleError.bind(this)));
  }

  public createMap(edition: string, map: any): Observable<any> {
    return this.http.post(`/api/${edition}/refsets/simple-map-to-snomed-with-correlation`, map).pipe(catchError(this.handleError.bind(this)));
  }

  public createTranslations(edition: string, translation: any): Observable<any> {
    return this.http.post(`/api/${edition}/translations`, translation).pipe(catchError(this.handleError.bind(this)));
  }

  public showCustomConcepts(edition: string): Observable<any> {
    return this.http.post(`/api/${edition}/concepts/show`, {}).pipe(catchError(this.handleError.bind(this)));
  }

  public hideCustomConcepts(edition: string): Observable<any> {
    return this.http.post(`/api/${edition}/concepts/hide`, {}).pipe(catchError(this.handleError.bind(this)));
  }

  public deleteEdition(edition: string): Observable<any> {
    return this.http.delete(`/api/codesystems/${edition}`).pipe(catchError(this.handleError.bind(this)));
  }

  public uploadRefsetToolTranslation(edition: string, refsetId: string, file: File): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);
    const apiUrl = `/api/${edition}/translations/${refsetId}/refset-tool`;
    return this.http.put(apiUrl, formData).pipe(catchError(this.handleError.bind(this)));
  }

  public uploadWeblateTranslation(edition: string, refsetId: string, file: File, languageCode: string): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);
    const apiUrl = `/api/${edition}/translations/${refsetId}/weblate?languageCode=${languageCode}`;
    return this.http.put(apiUrl, formData).pipe(catchError(this.handleError.bind(this)));
  }

  public uploadSpreadsheetRefset(edition: string, refsetId: string, file: File): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);
    const apiUrl = `/api/${edition}/refsets/simple/${refsetId}/spreadsheet`;
    return this.http.put(apiUrl, formData).pipe(catchError(this.handleError.bind(this)));
  }

  public uploadRefsetToolSubset(edition: string, refsetId: string, file: File): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);
    const apiUrl = `/api/${edition}/refsets/simple/${refsetId}/refset-tool`;
    return this.http.put(apiUrl, formData).pipe(catchError(this.handleError.bind(this)));
  }

  public uploadSpreadsheetMap(edition: string, refsetId: string, file: File): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);
    const apiUrl = `/api/${edition}/refsets/simple-map-to-snomed-with-correlation/${refsetId}/spreadsheet`;
    return this.http.put(apiUrl, formData).pipe(catchError(this.handleError.bind(this)));
  }

  public uploadConceptsSpreadsheet(edition: string, file: File): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);
    const apiUrl = `/api/${edition}/concepts/spreadsheet`;
    return this.http.put(apiUrl, formData).pipe(catchError(this.handleError.bind(this)));
  }

  // New download method
  public downloadConceptsSpreadsheet(edition: string): Observable<Blob> {
    const apiUrl = `/api/${edition}/concepts/spreadsheet`;
    return this.http.get(apiUrl, { responseType: 'blob' }).pipe(
      catchError(this.handleError.bind(this))
    );
  }

  public downloadRefsetSpreadsheet(edition: string, refsetId: string ): Observable<Blob> {
    const apiUrl = `/api/${edition}/refsets/simple/${refsetId}/spreadsheet`;
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
    const url = `/api/${edition}/concepts?offset=${offset}&limit=${limit}`;
    return this.http.get(url).pipe(catchError(this.handleError.bind(this)));
  }

  public startClassification(edition: string): Observable<any> {
    return this.http.post(`/api/codesystems/${edition}/classify`, null).pipe(catchError(this.handleError.bind(this)));
  }

  public getJobs(edition: string, refsetId?: string): Observable<any> {
    if (!refsetId) {
      return this.http.get(`/api/${edition}/jobs`).pipe(catchError(this.handleError.bind(this)));
    } else if (refsetId === 'concepts') {
      return this.http.get(`/api/${edition}/jobs?jobType=CONCEPT_CHANGE`).pipe(catchError(this.handleError.bind(this)));
    } else {
      return this.http.get(`/api/${edition}/jobs?refsetId=${refsetId}`).pipe(catchError(this.handleError.bind(this)));
    }
  }

  public getRoles(): Observable<any> {
    return this.http.get('/api/auth').pipe(catchError(this.handleError.bind(this)));
  }

  public startValidation(edition: string): Observable<any> {
    return this.http.post(`/api/codesystems/${edition}/validate`, null).pipe(catchError(this.handleError.bind(this)));
  }

  public getValidationResultSpreadsheet(edition: string): Observable<any> {
    const apiUrl = `/api/codesystems/${edition}/validate/spreadsheet`;
    return this.http.get(apiUrl, { responseType: 'blob' }).pipe(
      catchError(this.handleError.bind(this))
    );
  }


  public getCodeSystemReleaseStatus(edition: string): Observable<string> {
    return this.getEdition(edition).pipe(
      switchMap(codeSystem => {
        let status = '';
        if (!codeSystem.releasePreparation) {
          status = 'Authoring';
        } else if (codeSystem.releasePreparation && codeSystem.classificationStatus === 'TODO') {
          status = 'Content Cut-off';
        } else if (codeSystem.releasePreparation && codeSystem.classificationStatus === 'COMPLETE' &&
                  (codeSystem.validationStatus === 'TODO' || codeSystem.validationStatus === 'STALE')) {
          status = 'Classification Completed';
        } else if (codeSystem.releasePreparation && codeSystem.classificationStatus === 'COMPLETE' &&
                  (codeSystem.validationStatus === 'CONTENT_WARNING' || codeSystem.validationStatus === 'COMPLETE')) {
          status = 'Validation Completed';
        }
        return of(status);
      }),
      catchError(error => {
        console.error('Error fetching edition status:', error);
        // Return or throw an Observable error. This can be customized based on how you want to handle errors.
        return of('Error'); // This could be replaced with a more sophisticated error handling strategy.
      })
    );
  }


}

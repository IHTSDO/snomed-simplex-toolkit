import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class SimplexService {

  constructor(private http: HttpClient, private snackBar: MatSnackBar) { }

  private handleError(error: HttpErrorResponse) {
    if (error.status === 403) {
      const redirectUrl = `https://dev-ims.ihtsdotools.org/#/login?serviceReferer=${window.location.href}`;
      window.location.href = redirectUrl;
    } else if (error.status === 504) {
      this.snackBar.open(`Error: Can't contact simplex server`, 'Dismiss', {
        duration: 5000
      });
    } 
    // Return an observable with a user-facing error message
    console.error(error)
    return throwError('Something went wrong; please try again later.');
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
    console.log('downloadConceptsSpreadsheet');
    const apiUrl = `/api/${edition}/concepts/spreadsheet`;
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

  public startClassification(edition: string): Observable<any> {
    return this.http.post(`/api/codesystems/${edition}/classify`, null).pipe(catchError(this.handleError.bind(this)));
  }

  public getJobs(edition: string, refsetId: string): Observable<any> {
    if (refsetId === 'concepts') {
      return this.http.get(`/api/${edition}/jobs?jobType=CONCEPT_CHANGE`).pipe(catchError(this.handleError.bind(this)));
    } else {
      return this.http.get(`/api/${edition}/jobs?refsetId=${refsetId}`).pipe(catchError(this.handleError.bind(this)));
    }
  }
  
}

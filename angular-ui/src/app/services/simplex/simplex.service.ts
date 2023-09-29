import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class SimplexService {

  constructor(private http: HttpClient) { }

  private handleError(error: HttpErrorResponse) {
    if (error.status === 403) {
      const redirectUrl = `https://dev-ims.ihtsdotools.org/#/login?serviceReferer=${window.location.href}`;
      window.location.href = redirectUrl;
    }
    // Return an observable with a user-facing error message
    return throwError('Something went wrong; please try again later.');
  }

  public getEditions(): Observable<any> {
    return this.http.get('/api/codesystems').pipe(catchError(this.handleError.bind(this)));
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

  public deleteEdition(edition: string): Observable<any> {
    return this.http.delete(`/api/codesystems/${edition}`).pipe(catchError(this.handleError.bind(this)));
  }
}

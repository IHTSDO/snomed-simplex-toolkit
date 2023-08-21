import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SimplexService {

  constructor(private http: HttpClient) { }

  public getEditions(): Observable<any> {
    return this.http.get('/api/codesystems');
  }

  public getSimpleRefsets(edition: string): Observable<any> {
    return this.http.get(`/api/${edition}/refsets/simple`);
  }

  public getSimpleMap(edition: string): Observable<any> {
    return this.http.get(`/api/${edition}/refsets/simple-map-to-snomed-with-correlation`);
  }

  public getTranslation(edition: string): Observable<any> {
    return this.http.get(`/api/${edition}/translations`);
  }

  public createEdition(edition: any): Observable<any> {
    return this.http.post('/api/codesystems', edition);
  }

  public createSimpleRefset(edition: string, simpleRefset: any): Observable<any> {
    return this.http.post(`/api/${edition}/refsets/simple`, simpleRefset);
  }
}
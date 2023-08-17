import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SimplexService {

  constructor(private http: HttpClient) { }

  public getEditions(): Observable<any> {
    return this.http.get('https://dev-is-authoring.ihtsdotools.org/api/codesystems');
  }

  public getSimpleRefsets(edition: string): Observable<any> {
    return this.http.get(`https://dev-is-authoring.ihtsdotools.org/api/${edition}/refsets/simple`);
  }

  public getSimpleMap(edition: string): Observable<any> {
    return this.http.get(`https://dev-is-authoring.ihtsdotools.org/api/${edition}/refsets/simple-map-to-snomed-with-correlation`);
  }

  public getTranslation(edition: string): Observable<any> {
    return this.http.get(`https://dev-is-authoring.ihtsdotools.org/api/${edition}/translations`);
  }
}

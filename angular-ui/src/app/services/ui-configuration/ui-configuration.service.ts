import { Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class UiConfigurationService {

  private configuration: any;

  constructor() { }

  setConfiguration(configuration: any) {
    this.configuration = configuration;
  }

  getConfiguration(): Observable<any> {
    return this.configuration;
  }
}

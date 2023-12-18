import { Injectable } from '@angular/core';
import { CookieService } from 'ngx-cookie-service';

@Injectable({
  providedIn: 'root'
})
export class LegalAgreementService {
  private readonly AGREEMENT_COOKIE_NAME = 'legalAgreement';

  constructor(private cookieService: CookieService) {}

  setAgreement(agreed: boolean): void {
    const expiryDate = new Date();
    expiryDate.setFullYear(expiryDate.getFullYear() + 1); // 1 year from now
    this.cookieService.set(this.AGREEMENT_COOKIE_NAME, agreed ? 'yes' : 'no', expiryDate);
  }

  hasAgreed(): boolean {
    try {
      return this.cookieService.get(this.AGREEMENT_COOKIE_NAME) === 'yes';
    } catch (error) {
      console.error('Error reading agreement cookie:', error);
      return false;
    }
  }

  removeAgreement(): void {
    this.cookieService.delete(this.AGREEMENT_COOKIE_NAME);
  }

  // Additional utility methods can be added as needed
}

import { Component, OnInit } from '@angular/core';
import 'jquery';
import { Title } from '@angular/platform-browser';
import { EnvService } from './services/environment/env.service';
import { ToastrService } from 'ngx-toastr';
import {ModalService} from "./services/modal/modal.service";
import {Subscription, filter, lastValueFrom} from "rxjs";
import {NewCodesystem} from "./models/codesystem";
import { UiConfigurationService } from './services/ui-configuration/ui-configuration.service';
import { SimplexService } from './services/simplex/simplex.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { LegalAgreementService } from './services/legal-agreement/legal-agreement.service';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {

    toastrConfig = {
        closeButton: true
    };

    versions: object;
    environment: string;
    scheduledAlerts: any[] = [];

    activeCodesystem: any;
    activeCodesystemSubscription: Subscription;

    selectedEdition: any = null;
    showLegalModal: boolean = false;

    constructor(private envService: EnvService,
                private toastr: ToastrService,
                private titleService: Title,
                private route: ActivatedRoute,
                private router: Router,
                private simplexService: SimplexService,
                private snackBar: MatSnackBar,
                private uiConfigurationService: UiConfigurationService,
                private legalAgreementService: LegalAgreementService) {
    }

    async ngOnInit() {
        this.titleService.setTitle('Simplex');
        this.environment = this.envService.env;
        this.assignFavicon();
        if (!this.legalAgreementService.hasAgreed()) {
            // If the user hasn't agreed yet, show the modal
            this.showLegalModal = true;
        }
    }

    closeWelcome() {
        this.simplexService.refreshUIConfiguration();
        this.uiConfigurationService.getSelectedEdition().subscribe(edition => {
            this.selectedEdition = edition;
        });
        if (!this.legalAgreementService.hasAgreed()) {
            // If the user hasn't agreed yet, show the modal
            this.showLegalModal = true;
        } else {
            this.loadFirstEdition();
        }
    }

    assignFavicon() {
        const favicon = $('#favicon');

        switch (this.environment) {
            case 'local':
                favicon.attr('href', 'favicon_grey.ico');
                break;
            case 'dev':
                favicon.attr('href', 'favicon_red.ico');
                break;
            case 'uat':
                favicon.attr('href', 'favicon_green.ico');
                break;
            case 'training':
                favicon.attr('href', 'favicon_yellow.ico');
                break;
            default:
                favicon.attr('href', 'favicon.ico');
                break;
        }
    }

    isInHome(): boolean {
        return this.router.url === '/home';
    }

    cloneObject(object): any {
        return JSON.parse(JSON.stringify(object));
    }

    loadFirstEdition() {
        lastValueFrom(this.simplexService.getEditions()).then(
          (editions) => {
            // remove editions with empty name
            editions.items = editions.items.filter((item) => item.name);
            if (editions.items.length > 0) { this.selectedEdition = editions.items[0] } 
          },
          (error) => {
            this.snackBar.open('Failed to load editions', 'Dismiss', {
              duration: 5000
            });
          }
        );
    }

    handleUserResponse(agreed: boolean) {
        this.legalAgreementService.setAgreement(agreed);
        if (!agreed) {
            // If the user disagreed, redirect to https://snomed.org
            window.location.href = 'https://snomed.org';
        } else {
            this.showLegalModal = false;
            this.loadFirstEdition();
        }
    }
}

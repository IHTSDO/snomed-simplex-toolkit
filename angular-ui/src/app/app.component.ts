import { Component, OnInit } from '@angular/core';
import 'jquery';
import { Title } from '@angular/platform-browser';
import { AuthoringService } from './services/authoring/authoring.service';
import { BranchingService } from './services/branching/branching.service';
import { EnvService } from './services/environment/env.service';
import { ToastrService } from 'ngx-toastr';
import {SnowstormService} from "./services/snowstorm/snowstorm.service";
import {ModalService} from "./services/modal/modal.service";
import {Subscription} from "rxjs";
import {NewCodesystem} from "./models/codesystem";

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

    constructor(private authoringService: AuthoringService,
                private branchingService: BranchingService,
                private envService: EnvService,
                private toastr: ToastrService,
                private titleService: Title,
                private snowstormService: SnowstormService,
                private modalService: ModalService) {
        this.activeCodesystemSubscription = this.snowstormService.getActiveCodesystem().subscribe(data => this.activeCodesystem = data);
    }

    ngOnInit() {
        this.titleService.setTitle('SNOMED CT Snowstorm Admin');
        this.environment = this.envService.env;

        this.assignFavicon();

        this.snowstormService.httpGetCodesystems().subscribe(data => {
            this.snowstormService.setCodesystems(data);
            this.snowstormService.setActiveCodesystem(data['items'][0]);
        });
    }

    openModal(id: string): void {
        this.modalService.open(id);
    }

    closeModal(id: string): void {
        this.modalService.close(id);
    }

    newCodesystem(name, owner, maintainerType, shortName, branchPath, countryCode, defaultLanguageCode) {
        let newCodesystem: NewCodesystem = new NewCodesystem(name, owner, maintainerType, [], countryCode, shortName, branchPath, 0, defaultLanguageCode)
        console.log('newCodesystem: ', newCodesystem);
    }

    addRole(role) {
        console.log('role: ', role);
    }

    addModule(conceptId) {
        console.log('conceptId: ', conceptId);
    }

    addLanguage(key, value) {
        console.log('key: ', key);
        console.log('value: ', value);
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

    cloneObject(object): any {
        return JSON.parse(JSON.stringify(object));
    }
}

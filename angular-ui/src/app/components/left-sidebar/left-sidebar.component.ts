import { Component, OnInit } from '@angular/core';
import {Subscription} from 'rxjs';
import {SnowstormService} from "../../services/snowstorm/snowstorm.service";
import {ModalService} from "../../services/modal/modal.service";

@Component({
    selector: 'app-left-sidebar',
    templateUrl: './left-sidebar.component.html',
    styleUrls: ['./left-sidebar.component.scss']
})
export class LeftSidebarComponent implements OnInit {

    textFilter: string;

    codesystems: any;
    codesystemsSubscription: Subscription;
    activecodesystem: any;
    activecodesystemSubscription: Subscription;

    chapters = [
        'Introduction',
        'Colours',
        'Typeface',
        'Forms',
        'Modals',
        'Toastr',
        'Libraries',
        'Learning'
    ];

    constructor(private snowstormService: SnowstormService, private modalService: ModalService) {
        this.codesystemsSubscription = this.snowstormService.getCodesystems().subscribe( data => this.codesystems = data);
        this.activecodesystemSubscription = this.snowstormService.getActiveCodesystem().subscribe( data => this.activecodesystem = data);
    }

    ngOnInit() {
    }

    openModal(id: string): void {
        this.modalService.open(id);
    }

    closeModal(id: string): void {
        this.modalService.close(id);
    }

    setActiveCodesystem(codesystem): void {
        this.snowstormService.setActiveCodesystem(codesystem);
    }

    cloneObject(object): any {
        return JSON.parse(JSON.stringify(object));
    }
}

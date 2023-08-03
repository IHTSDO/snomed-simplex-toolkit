import {Component, OnInit} from '@angular/core';
import {ModalService} from '../../services/modal/modal.service';

@Component({
    selector: 'app-main-view',
    templateUrl: './main-view.component.html',
    styleUrls: ['./main-view.component.scss']
})
export class MainViewComponent implements OnInit {

    activeTab: string = 'codesystems';

    constructor(private modalService: ModalService) {
    }

    ngOnInit(): void {
    }

    openModal(id: string): void {
        this.modalService.open(id);
    }

    closeModal(id: string): void {
        this.modalService.close(id);
    }
}

import { Component, OnInit } from '@angular/core';
import { ModalService } from '../../services/modal/modal.service';

@Component({
    selector: 'app-main-view',
    templateUrl: './main-view.component.html',
    styleUrls: ['./main-view.component.scss']
})
export class MainViewComponent implements OnInit {

    selectedEdition: any = null;
    changingEdition = false;

    constructor(private modalService: ModalService) {
    }

    ngOnInit(): void {
    }


    editionSelected(edition: any): void {
        this.selectedEdition = edition;
        this.changingEdition = true;
        setTimeout(() => {
            this.changingEdition = false;
        }, 1000);
    }

    openModal(id: string): void {
        this.modalService.open(id);
    }

    closeModal(id: string): void {
        this.modalService.close(id);
    }
}

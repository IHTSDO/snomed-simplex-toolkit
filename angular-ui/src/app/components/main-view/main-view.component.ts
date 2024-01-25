import { Component, Input, OnInit } from '@angular/core';
import { ModalService } from '../../services/modal/modal.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';

@Component({
    selector: 'app-main-view',
    templateUrl: './main-view.component.html',
    styleUrls: ['./main-view.component.scss']
})
export class MainViewComponent implements OnInit {

    @Input() selectedEdition: any = null;
    changingEdition = false;

    selectedMenuItem = 'artifacts';

    constructor(private modalService: ModalService, private uiConfigurationService: UiConfigurationService) {
    }

    ngOnInit(): void {
        this.uiConfigurationService.getSelectedEdition().subscribe(edition => {
            if (edition && (edition.id !== this.selectedEdition?.id)) {
                this.editionSelected(edition);
            }
        });
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

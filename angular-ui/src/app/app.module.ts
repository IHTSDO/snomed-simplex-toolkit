import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { AppComponent } from './app.component';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { HeaderInterceptor } from './interceptors/header.interceptor';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { SnomedNavbarComponent } from './components/snomed-navbar/snomed-navbar.component';
import { SnomedFooterComponent } from './components/snomed-footer/snomed-footer.component';
import { AuthenticationService } from './services/authentication/authentication.service';
import { AuthoringService } from './services/authoring/authoring.service';
import { EnvServiceProvider } from './providers/env.service.provider';
import {ToastrModule} from 'ngx-toastr';
import {StatusPageService} from './services/statusPage/status-page.service';
import {PathingService} from './services/pathing/pathing.service';
import {AlphabeticalPipe} from './pipes/alphabetical/alphabetical.pipe';
import {BranchPipe} from './pipes/branch/branch.pipe';
import {ProjectPipe} from './pipes/project/project.pipe';
import {AppRoutingModule} from './app-routing.module';
import {ModalService} from './services/modal/modal.service';
import {ModalComponent} from './components/modal/modal.component';
import {TextFilterPipe} from './pipes/text-filter/text-filter.pipe';
import {ConceptService} from './services/concept/concept.service';
import { MainViewComponent } from './components/main-view/main-view.component';
import { CodesystemsComponent } from './components/codesystems/codesystems.component';
import { BootConfigComponent } from './components/boot-config/boot-config.component';
import { SyndicationComponent } from './components/syndication/syndication.component';
import { RbacComponent } from './components/rbac/rbac.component';
import { StringComponent } from './components/codesystems/types/string/string.component';
import { NumberComponent } from './components/codesystems/types/number/number.component';
import { BooleanComponent } from './components/codesystems/types/boolean/boolean.component';
import { ArrayComponent } from './components/codesystems/types/array/array.component';
import { ObjectComponent } from './components/codesystems/types/object/object.component';
import {MatTabsModule} from '@angular/material/tabs';
import {MatIconModule} from '@angular/material/icon';
import { SelectEditionComponent } from './components/select-edition/select-edition.component';
import {MatTableModule} from '@angular/material/table';
import {MatListModule} from '@angular/material/list';
import { MatRippleModule } from '@angular/material/core';
import { SubsetsComponent } from './components/subsets/subsets.component';
import { MapsComponent } from './components/maps/maps.component';
import { TranslationsComponent } from './components/translations/translations.component';
import { PublishComponent } from './components/publish/publish.component';
import {MatButtonModule} from '@angular/material/button';
import { NewEditionComponent } from './components/new-edition/new-edition.component';
import { ReactiveFormsModule } from '@angular/forms'; // Import the ReactiveFormsModule
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import { CamelToTitlePipe } from './pipes/camel-to-title/camel-to-title.pipe';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatSnackBarModule} from '@angular/material/snack-bar';
import { ArtifactsComponent } from './components/artifacts/artifacts.component';
import {MatSelectModule} from '@angular/material/select';
import { JobsComponent } from './components/jobs/jobs.component';
import { TimeAgoPipe } from './pipes/timeAgo/time-ago.pipe';
import { ConceptsListComponent } from './components/artifacts/concepts-list/concepts-list.component';
import { MatPaginatorModule } from '@angular/material/paginator';
import {MatSidenavModule} from '@angular/material/sidenav';
import {MatRadioModule} from '@angular/material/radio';
import { CookieService } from 'ngx-cookie-service';
import { LegalAgreementComponent } from './components/legal-agreement/legal-agreement.component';

// SERVICE IMPORTS


@NgModule({
    declarations: [
        AppComponent,
        SnomedNavbarComponent,
        SnomedFooterComponent,
        AlphabeticalPipe,
        BranchPipe,
        ProjectPipe,
        ModalComponent,
        TextFilterPipe,
        MainViewComponent,
        CodesystemsComponent,
        BootConfigComponent,
        SyndicationComponent,
        RbacComponent,
        StringComponent,
        NumberComponent,
        BooleanComponent,
        ArrayComponent,
        ObjectComponent,
        SelectEditionComponent,
        SubsetsComponent,
        MapsComponent,
        TranslationsComponent,
        PublishComponent,
        NewEditionComponent,
        CamelToTitlePipe,
        ArtifactsComponent,
        JobsComponent,
        TimeAgoPipe,
        ConceptsListComponent,
        LegalAgreementComponent
        ],
    imports: [
        BrowserModule,
        FormsModule,
        HttpClientModule,
        BrowserAnimationsModule,
        NgbTypeaheadModule,
        AppRoutingModule,
        ToastrModule.forRoot(),
        MatTabsModule,
        MatIconModule,
        MatTableModule,
        MatListModule,
        MatRippleModule,
        MatButtonModule,
        ReactiveFormsModule,
        MatFormFieldModule,
        MatInputModule,
        MatProgressSpinnerModule,
        MatSnackBarModule,
        MatSelectModule,
        MatPaginatorModule,
        MatSidenavModule,
        MatRadioModule
    ],
    providers: [
        AuthenticationService,
        AuthoringService,
        StatusPageService,
        ModalService,
        PathingService,
        ConceptService,
        CookieService,
        EnvServiceProvider,
        // {
        //     provide: HTTP_INTERCEPTORS,
        //     useClass: HeaderInterceptor,
        //     multi: true
        // }
    ],
    bootstrap: [AppComponent]
})
export class AppModule {
}

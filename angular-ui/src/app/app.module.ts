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
import {LeftSidebarComponent} from './components/left-sidebar/left-sidebar.component';
import {TextFilterPipe} from './pipes/text-filter/text-filter.pipe';
import {ConceptService} from './services/concept/concept.service';
import { MainViewComponent } from './components/main-view/main-view.component';
import { CodesystemsComponent } from './components/codesystems/codesystems.component';
import { BootConfigComponent } from './components/boot-config/boot-config.component';
import { SyndicationComponent } from './components/syndication/syndication.component';
import { RbacComponent } from './components/rbac/rbac.component';
import {SnowstormService} from "./services/snowstorm/snowstorm.service";
import { StringComponent } from './components/codesystems/types/string/string.component';
import { NumberComponent } from './components/codesystems/types/number/number.component';
import { BooleanComponent } from './components/codesystems/types/boolean/boolean.component';
import { ArrayComponent } from './components/codesystems/types/array/array.component';
import { ObjectComponent } from './components/codesystems/types/object/object.component';

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
        LeftSidebarComponent,
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
        ObjectComponent
    ],
    imports: [
        BrowserModule,
        FormsModule,
        HttpClientModule,
        BrowserAnimationsModule,
        NgbTypeaheadModule,
        AppRoutingModule,
        ToastrModule.forRoot()
    ],
    providers: [
        AuthenticationService,
        AuthoringService,
        StatusPageService,
        ModalService,
        PathingService,
        ConceptService,
        SnowstormService,
        EnvServiceProvider,
        {
            provide: HTTP_INTERCEPTORS,
            useClass: HeaderInterceptor,
            multi: true
        }
    ],
    bootstrap: [AppComponent]
})
export class AppModule {
}

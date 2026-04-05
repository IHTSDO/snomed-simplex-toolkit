import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtifactsComponent } from './components/artifacts/artifacts.component';
import { DownloadReleasesComponent } from './components/download-releases/download-releases.component';
import { ManageCodesystemComponent } from './components/manage-codesystem/manage-codesystem.component';
import { SelectEditionComponent } from './components/select-edition/select-edition.component';
import { WelcomeComponent } from './components/welcome/welcome.component';
import { AdminSettingsComponent } from './components/admin-settings/admin-settings.component';
import { TranslationDashboardComponent } from './components/translation-dashboard/translation-dashboard.component';

const routes: Routes = [
  { path: 'home', component: WelcomeComponent },
  { path: 'admin', component: AdminSettingsComponent },
  { path: 'artifacts', component: ArtifactsComponent },
  { path: 'artifact/:edition', component: ArtifactsComponent },
  { path: 'manage', component: ManageCodesystemComponent },
  { path: 'manage/:edition', component: ManageCodesystemComponent },
  { path: 'info', component: SelectEditionComponent },
  { path: 'info/:edition', component: SelectEditionComponent },
  { path: 'releases', component: DownloadReleasesComponent },
  { path: 'releases/:edition', component: DownloadReleasesComponent },
  { path: 'translation-dashboard/:edition', redirectTo: 'translation-studio/:edition', pathMatch: 'full' },
  { path: 'translation-dashboard', redirectTo: 'translation-studio', pathMatch: 'full' },
  { path: 'translation-studio', component: TranslationDashboardComponent },
  { path: 'translation-studio/:edition/:refset/:label', component: TranslationDashboardComponent },
  { path: 'translation-studio/:edition', component: TranslationDashboardComponent },
  { path: '', redirectTo: '/home', pathMatch: 'full' }
];


@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }

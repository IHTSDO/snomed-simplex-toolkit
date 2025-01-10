import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtifactsComponent } from './components/artifacts/artifacts.component';
import { DownloadReleasesComponent } from './components/download-releases/download-releases.component';
import { ManageCodesystemComponent } from './components/manage-codesystem/manage-codesystem.component';
import { SelectEditionComponent } from './components/select-edition/select-edition.component';
import { WelcomeComponent } from './components/welcome/welcome.component';
import { AdminSettingsComponent } from './components/admin-settings/admin-settings.component';

const routes: Routes = [
    { path: 'home', component: WelcomeComponent },
    { path: 'admin', component: AdminSettingsComponent },
    { path: 'artifacts', component: ArtifactsComponent },
    { path: 'artifact/:edition?', component: ArtifactsComponent },
    { path: 'manage/:edition?', component: ManageCodesystemComponent },
    { path: 'info/:edition?', component: SelectEditionComponent },
    { path: 'releases/:edition?', component: DownloadReleasesComponent },
    { path: '', redirectTo: '/home', pathMatch: 'full' }
];
  

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}

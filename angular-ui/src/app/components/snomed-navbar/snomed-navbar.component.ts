import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Subscription, lastValueFrom } from 'rxjs';
import { User } from '../../models/user';
import { AuthenticationService } from '../../services/authentication/authentication.service';
import {PathingService} from '../../services/pathing/pathing.service';
import {Location} from '@angular/common';
import { LegalAgreementService } from 'src/app/services/legal-agreement/legal-agreement.service';
import { SimplexService } from 'src/app/services/simplex/simplex.service';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';

@Component({
    selector: 'app-snomed-navbar',
    templateUrl: './snomed-navbar.component.html',
    styleUrls: ['./snomed-navbar.component.scss']
})
export class SnomedNavbarComponent implements OnInit {

    @Input() selectedEdition: any = null;
    @Output() home = new EventEmitter<any>();

    environment: string;
    path: string;

    user: User;
    userSubscription: Subscription;

    branches: any;
    branchesSubscription: Subscription;
    activeBranch: any;
    activeBranchSubscription: Subscription;

    projects: any;
    projectsSubscription: Subscription;
    activeProject: any;
    activeProjectSubscription: Subscription;

    tasks: any;
    tasksSubscription: Subscription;
    activeTask: any;
    activeTaskSubscription: Subscription;

    editions = [];
    loading = false;

    constructor(
        private authenticationService: AuthenticationService, 
        private pathingService: PathingService, 
        private location: Location, 
        private legalAgreementService: LegalAgreementService,
        private uiConfigurationService: UiConfigurationService,
        private simplexService: SimplexService) {
            this.environment = window.location.host.split(/[.]/)[0].split(/[-]/)[0];
            this.userSubscription = this.authenticationService.getUser().subscribe(data => this.user = data);
            this.branchesSubscription = this.pathingService.getBranches().subscribe(data => this.branches = data);
            this.activeBranchSubscription = this.pathingService.getActiveBranch().subscribe(data => this.activeBranch = data);
            this.projectsSubscription = this.pathingService.getProjects().subscribe(data => this.projects = data);
            this.activeProjectSubscription = this.pathingService.getActiveProject().subscribe(data => this.activeProject = data);
            this.tasksSubscription = this.pathingService.getTasks().subscribe(data => this.tasks = data);
            this.activeTaskSubscription = this.pathingService.getActiveTask().subscribe(data => this.activeTask = data);
        }

    ngOnInit() {
        // this.authenticationService.setUser();
        this.path = this.location.path();
        this.loadEditions();
        // this.pathingService.httpGetBranches().subscribe(branches => {
        //     this.pathingService.setBranches(branches);
        //     if (!this.path) {
        //         this.pathingService.setActiveBranch(branches[0]);
        //     }
        // });
        //
        // this.pathingService.httpGetProjects().subscribe(projects => {
        //     this.pathingService.setProjects(projects);
        //     this.setPath(this.path);
        // });
    }

    loadEditions() {
        this.editions = [];
        this.loading = true;
        lastValueFrom(this.simplexService.getEditions()).then(
          (editions) => {
            // remove editions with empty name
            editions.items = editions.items.filter((item) => item.name); 
            this.editions = editions.items;
            this.loading = false;
          },
          (error) => {
            console.error(error);
          }
        );
      }


    setPath(path) {
        const splitPath = path.split('?')[0].split('/');
        this.setBranch({ branchPath: 'MAIN'});
        if (path.includes('SNOMEDCT')) {
            if (splitPath.length > 2) {
                this.setBranch({ branchPath: splitPath[1] + '/' + splitPath[2]});
            }

            if (splitPath.length > 3) {
                this.setProject({ key: splitPath[3]});
            }

            if (splitPath.length > 4) {
                this.setTask({ key: splitPath[4]});
            }
        } else {
            if (splitPath.length > 1) {
                this.setBranch({ branchPath: splitPath[1]});
            }

            if (splitPath.length > 2) {
                this.setProject({ key: splitPath[2]});
            }

            if (splitPath.length > 3) {
                this.setTask({ key: splitPath[3]});
            }
        }
    }

    setBranch(branch) {
        this.pathingService.setActiveBranch(branch);

        this.pathingService.setActiveProject(null);

        this.pathingService.setTasks([]);
        this.pathingService.setActiveTask(null);
    }

    setProject(project) {
        const proj = this.projects.find(item => item.key === project.key);
        this.pathingService.setActiveProject(proj);

        this.pathingService.setTasks([]);
        this.pathingService.setActiveTask(null);

        if (proj.key) {
            this.pathingService.httpGetTasks(proj).subscribe(tasks => {
                this.pathingService.setTasks(tasks);
            });
        }
    }

    noProject() {
        this.pathingService.setActiveProject(null);
    }

    setTask(task) {
        this.pathingService.setActiveTask(task);
    }

    noTask() {
        this.pathingService.setActiveTask(null);
    }

    logout() {
        this.simplexService.logout();
        // this.simplexService.logoutAccount().subscribe(() => {
        //     this.goHome();
        // });
    }

    removeAgreementAndReload() {
        this.legalAgreementService.removeAgreement();
        window.location.reload();
    }

    selectEdition(item: any) {
        this.selectedEdition = item;
        this.uiConfigurationService.setSelectedEdition(item);
    }

    goHome() {
        this.home.emit();
    }
    
}

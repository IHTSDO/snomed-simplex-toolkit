import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { UiConfigurationService } from 'src/app/services/ui-configuration/ui-configuration.service';

@Component({
  selector: 'app-main-view',
  templateUrl: './main-view.component.html',
  styleUrls: ['./main-view.component.scss']
})
export class MainViewComponent implements OnInit {
  selectedEdition: string;
  selectedMenuItem: string;

  constructor(private router: Router, private route: ActivatedRoute, private uiService: UiConfigurationService,private changeDetectorRef: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.uiService.getSelectedEdition().subscribe(edition => {
      if (edition) {
        this.selectedEdition = edition.shortName;
      }
    });
    // Initialize selectedMenuItem based on the current URL
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe(() => {
      this.updateSelectedMenuItemBasedOnUrl(this.router.url);
    });
  }

  navigateTo(menuItem: string): void {
    this.selectedMenuItem = menuItem;
    this.router.navigate([`/${menuItem}`, this.selectedEdition]); // Use Angular router to update the URL
  }

  isInHome(): boolean {
    return this.router.url === '/home';
  }

  private updateSelectedMenuItemBasedOnUrl(url: string): void {
    if (url) {
      if (url.includes('artifact')) {
        this.selectedMenuItem = 'artifacts';
      } else if (url.includes('manage')) {
        this.selectedMenuItem = 'manage';
      } else if (url.includes('info')) {
        this.selectedMenuItem = 'info';
      } else if (url.includes('releases')) {
        this.selectedMenuItem = 'releases';
      }
      this.changeDetectorRef.detectChanges();
    }
  }
}

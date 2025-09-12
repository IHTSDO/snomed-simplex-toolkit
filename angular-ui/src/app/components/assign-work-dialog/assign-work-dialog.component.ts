import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SimplexService } from '../../services/simplex/simplex.service';

export interface WeblateUser {
  id: number;
  username: string;
  full_name: string;
  email: string;
}

export interface UserWorkAssignment {
  user: WeblateUser;
  workPercentage: number;
}

export interface AssignWorkDialogData {
  edition: string;
  refsetId: string;
  labelSetName: string;
  label: string;
}

@Component({
  selector: 'app-assign-work-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatListModule,
    MatProgressSpinnerModule
  ],
  template: `
    <h2 mat-dialog-title>
      {{ currentPage === 'selection' ? 'Select Users' : 'Assign Work' }}
    </h2>
    <mat-dialog-content>
      <!-- Page 1: User Selection -->
      <div *ngIf="currentPage === 'selection'">
        <p>Select users to assign work for: <strong>{{ data.labelSetName }}</strong></p>
        
        <div *ngIf="loading" class="loading-container">
          <mat-spinner diameter="40"></mat-spinner>
          <p>Loading users...</p>
        </div>
        
        <div *ngIf="!loading && users.length === 0" class="no-users">
          <p>No users found for this translation team.</p>
        </div>
        
        <div *ngIf="!loading && users.length > 0">
          <mat-selection-list #userSelection (selectionChange)="onSelectionChange($event)">
            <mat-list-option 
              *ngFor="let user of users" 
              [value]="user">
              <div class="user-item">
                <strong>{{ user.full_name }}</strong>
              </div>
            </mat-list-option>
          </mat-selection-list>
          
          <p class="selection-info">
            Selected: {{ selectedUsers.length }} user(s)
          </p>
        </div>
      </div>
      
      <!-- Page 2: Work Assignment -->
      <div *ngIf="currentPage === 'assignment'">
        <p>Distribute work percentage among selected users for: <strong>{{ data.labelSetName }}</strong></p>
        
        <div class="selected-users-summary">
          <h4>Selected Users ({{ selectedUsers.length }})</h4>
          <ul>
            <li *ngFor="let user of selectedUsers">{{ user.full_name }}</li>
          </ul>
        </div>
        
        <div class="work-assignment-section">
          <h4>Work Distribution</h4>
          <p class="assignment-info">Adjust the sliders to distribute work percentage:</p>
          
          <div *ngFor="let assignment of userAssignments" class="assignment-item">
            <div class="user-assignment-header">
              <strong>{{ assignment.user.full_name }}</strong>
              <span class="percentage-display">{{ assignment.workPercentage }}%</span>
            </div>
            <div class="slider-container">
              <input 
                type="range" 
                [min]="0" 
                [max]="100" 
                [step]="5"
                [value]="assignment.workPercentage"
                (input)="onSliderInput($event, assignment)"
                class="work-slider">
            </div>
          </div>
          
          <div class="total-percentage">
            <strong>Total: {{ getTotalPercentage() }}%</strong>
            <span *ngIf="getTotalPercentage() !== 100" class="warning-text">
              (Should equal 100%)
            </span>
          </div>
        </div>
      </div>
    </mat-dialog-content>
    
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">Cancel</button>
      
      <!-- Back button for assignment page -->
      <button 
        *ngIf="currentPage === 'assignment'"
        mat-button 
        (click)="goBack()">
        Back
      </button>
      
      <!-- Next button for selection page -->
      <button 
        *ngIf="currentPage === 'selection'"
        mat-raised-button 
        color="primary" 
        [disabled]="loading || selectedUsers.length === 0"
        (click)="nextPage()">
        Next
      </button>
      
      <!-- Assign button for assignment page -->
      <button 
        *ngIf="currentPage === 'assignment'"
        mat-raised-button 
        color="primary" 
        [disabled]="getTotalPercentage() !== 100"
        (click)="onAssign()">
        Assign Work
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .loading-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 20px;
    }
    
    .no-users {
      text-align: center;
      padding: 20px;
      color: #666;
    }
    
    .user-item {
      display: flex;
      align-items: center;
    }
    
    .selection-info {
      margin-top: 16px;
      color: #666;
      font-size: 0.9em;
    }
    
    .work-assignment-section {
      margin-top: 24px;
      padding-top: 16px;
      border-top: 1px solid #e0e0e0;
    }
    
    .assignment-info {
      color: #666;
      font-size: 0.9em;
      margin-bottom: 16px;
    }
    
    .assignment-item {
      margin-bottom: 20px;
    }
    
    .user-assignment-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
    }
    
    .percentage-display {
      font-weight: bold;
      color: #1976d2;
      min-width: 40px;
      text-align: right;
    }
    
    .total-percentage {
      margin-top: 16px;
      padding: 12px;
      background-color: #f5f5f5;
      border-radius: 4px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    
    .warning-text {
      color: #f44336;
      font-size: 0.9em;
    }
    
    .selected-users-summary {
      margin-bottom: 24px;
      padding: 16px;
      background-color: #f8f9fa;
      border-radius: 4px;
      border-left: 4px solid #1976d2;
    }
    
    .selected-users-summary h4 {
      margin: 0 0 12px 0;
      color: #1976d2;
    }
    
    .selected-users-summary ul {
      margin: 0;
      padding-left: 20px;
    }
    
    .selected-users-summary li {
      margin-bottom: 4px;
      color: #333;
    }
    
    .slider-container {
      margin: 8px 0;
    }
    
    .work-slider {
      width: 100%;
      height: 6px;
      border-radius: 3px;
      background: #e0e0e0;
      outline: none;
      -webkit-appearance: none;
    }
    
    .work-slider::-webkit-slider-thumb {
      -webkit-appearance: none;
      appearance: none;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: #1976d2;
      cursor: pointer;
    }
    
    .work-slider::-moz-range-thumb {
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: #1976d2;
      cursor: pointer;
      border: none;
    }
    
    mat-dialog-content {
      min-width: 400px;
      max-height: 400px;
    }
  `]
})
export class AssignWorkDialogComponent implements OnInit {
  users: WeblateUser[] = [];
  selectedUsers: WeblateUser[] = [];
  userAssignments: UserWorkAssignment[] = [];
  loading = false;
  currentPage: 'selection' | 'assignment' = 'selection';

  constructor(
    public dialogRef: MatDialogRef<AssignWorkDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: AssignWorkDialogData,
    private simplexService: SimplexService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading = true;
    this.simplexService.getWeblateUsersForRefset(this.data.edition, this.data.refsetId)
      .subscribe({
        next: (users: WeblateUser[]) => {
          // Sort users by full name alphabetically
          this.users = users.sort((a, b) => a.full_name.localeCompare(b.full_name));
          this.loading = false;
        },
        error: (error) => {
          console.error('Error loading users:', error);
          this.snackBar.open('Error loading users. Please try again.', 'Close', {
            duration: 3000
          });
          this.loading = false;
        }
      });
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSelectionChange(event: any): void {
    this.selectedUsers = event.source.selectedOptions.selected.map((option: any) => option.value);
    this.updateUserAssignments();
  }

  updateUserAssignments(): void {
    // Remove assignments for unselected users
    this.userAssignments = this.userAssignments.filter(assignment => 
      this.selectedUsers.some(user => user.id === assignment.user.id)
    );
    
    // Add assignments for newly selected users
    this.selectedUsers.forEach(user => {
      const existingAssignment = this.userAssignments.find(assignment => assignment.user.id === user.id);
      if (!existingAssignment) {
        this.userAssignments.push({
          user: user,
          workPercentage: 0
        });
      }
    });
    
    // Distribute percentages evenly if there are selected users
    if (this.selectedUsers.length > 0) {
      const evenPercentage = Math.floor(100 / this.selectedUsers.length);
      const remainder = 100 % this.selectedUsers.length;
      
      this.userAssignments.forEach((assignment, index) => {
        assignment.workPercentage = evenPercentage + (index < remainder ? 1 : 0);
      });
    }
  }

  onSliderInput(event: Event, assignment: UserWorkAssignment): void {
    const target = event.target as HTMLInputElement;
    assignment.workPercentage = parseInt(target.value, 10);
  }

  getTotalPercentage(): number {
    return this.userAssignments.reduce((total, assignment) => total + assignment.workPercentage, 0);
  }

  nextPage(): void {
    this.currentPage = 'assignment';
  }

  goBack(): void {
    this.currentPage = 'selection';
  }

  onAssign(): void {
    // Call the API to assign work to users
    this.loading = true;
    this.simplexService.assignWorkToUsers(
      this.data.edition, 
      this.data.refsetId, 
      this.data.label, 
      this.userAssignments
    ).subscribe({
      next: () => {
        this.loading = false;
        this.dialogRef.close({
          action: 'assign',
          assignments: this.userAssignments
        });
      },
      error: (error) => {
        console.error('Error assigning work:', error);
        this.loading = false;
        this.snackBar.open('Error assigning work. Please try again.', 'Close', {
          duration: 3000
        });
      }
    });
  }
}

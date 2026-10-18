import { Routes } from '@angular/router';
import { IncidentDashboardComponent } from './features/incidents/incident-dashboard.component';

export const routes: Routes = [
  { path: '', component: IncidentDashboardComponent },
  { path: '**', redirectTo: '' }
];

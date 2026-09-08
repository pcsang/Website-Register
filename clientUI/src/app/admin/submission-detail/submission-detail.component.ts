import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

/**
 * Placeholder page for viewing a single submission's detail.
 * Route parameter reading is wired up here so routing can be verified end to end;
 * the actual submission detail UI/data-fetching is implemented in a later phase.
 */
@Component({
  selector: 'app-submission-detail',
  imports: [],
  templateUrl: './submission-detail.component.html',
  styleUrl: './submission-detail.component.scss'
})
export class SubmissionDetailComponent {
  private readonly route = inject(ActivatedRoute);

  /** The `id` route parameter for the submission being viewed, taken from the current route snapshot. */
  readonly id = this.route.snapshot.paramMap.get('id');
}

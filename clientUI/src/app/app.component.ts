import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Application shell. Each route family (public form, admin login, admin area) owns its
 * own header/layout, so this root component is just a router outlet — see
 * `AdminLayoutComponent` for the admin sidebar/topbar and `InformationFormComponent` for the
 * public page's own simple header.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'clientUI';
}

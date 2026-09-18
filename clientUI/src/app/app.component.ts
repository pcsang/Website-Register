import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Application shell. Each route family (public landing page, admin login, admin area) owns
 * its own header/layout, so this root component is just a router outlet — see
 * `AdminLayoutComponent` for the admin sidebar/topbar and `LandingPageComponent` for the
 * public page's own nav bar.
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

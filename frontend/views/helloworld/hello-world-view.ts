import { showNotification } from '@vaadin/flow-frontend/a-notification';
import '@vaadin/vaadin-button';
import '@vaadin/vaadin-text-field';
import { startCountdown } from 'Frontend/fake-generated/MyPushEndpoint';
import { customElement, html } from 'lit-element';
import { View } from '../../views/view';

@customElement('hello-world-view')
export class HelloWorldView extends View {
  name = '';

  render() {
    return html`
      <vaadin-text-field label="Your name" @value-changed=${this.nameChanged}></vaadin-text-field>
      <vaadin-button @click=${this.sayHello}>Say hello</vaadin-button>
    `;
  }

  nameChanged(e: CustomEvent) {
    this.name = e.detail.value;
  }

  async sayHello() {
    const countdown = startCountdown(this.name, 5);

    for await (const callout of countdown) {
      showNotification(callout);
    }
  }
}

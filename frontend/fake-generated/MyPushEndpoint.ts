import { open, EndpointGenerator } from 'Frontend/fake-generated/pushclient';
export function startCountdown(name: string, duration: number): EndpointGenerator<string> {
  return open('myPushEndpoint', 'startCountdown', [name, duration]);
}
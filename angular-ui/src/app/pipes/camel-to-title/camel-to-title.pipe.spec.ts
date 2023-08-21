import { CamelToTitlePipe } from './camel-to-title.pipe';

describe('CamelToTitlePipe', () => {
  it('create an instance', () => {
    const pipe = new CamelToTitlePipe();
    expect(pipe).toBeTruthy();
  });
});

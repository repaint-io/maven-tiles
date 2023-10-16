import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	site: 'https://repaint-io.github.io',
  // base: '/maven-tiles',
	integrations: [
		starlight({
			title: 'Tiles - Mixins For Apache Maven',
			social: {
				github: 'https://github.com/repaint-io/maven-tiles',
			},
			sidebar: [
				{
					label: 'Maven Tiles',
					items: [
						{ label: "About", link: '/introduction/about/'},
						{ label: "Using", link: '/introduction/using/'},
						{ label: "Writing", link: '/introduction/writing/'}
					]
				},
				// {
				// 	label: 'Guides',
				// 	items: [
				// 		// Each item here is one entry in the navigation menu.
				// 		{ label: 'Example Guide', link: '/guides/example/' },
				// 	],
				// },
				// {
				// 	label: 'Reference',
				// 	autogenerate: { directory: 'reference' },
				// },
			],
		}),
	],
});

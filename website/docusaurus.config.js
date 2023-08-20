module.exports = {
  title: 'ZIO gRPC',
  tagline: 'Build gRPC clients and servers with ZIO',
  url: 'https://scalapb.github.io',
  baseUrl: '/zio-grpc/',
  favicon: 'img/favicon.ico',
  organizationName: 'scalapb', // Usually your GitHub org/user name.
  projectName: 'zio-grpc', // Usually your repo name.
  themeConfig: {
    image: 'https://scalapb.github.io/zio-grpc/img/zio-grpc-social-light.png',
    navbar: {
      title: ' ',
      logo: {
        alt: 'ZIO gRPC',
        src: 'img/zio-grpc-hero.png',
      },
      items: [
        {
          to: 'docs/',
          activeBasePath: 'docs',
          label: 'Docs',
          position: 'left',
        },
        // {to: 'blog', label: 'Blog', position: 'left'},
        {
            'type': 'docsVersionDropdown',
            'position': 'right'
        },
        {
          href: 'https://github.com/scalapb/zio-grpc',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {
              label: 'Introduction',
              to: 'docs/',
            },
            {
              label: 'Installation',
              to: 'docs/installation',
            },
          ],
        },
        {
          title: 'Community',
          items: [
            {
              label: 'Stack Overflow',
              href: 'https://stackoverflow.com/questions/tagged/scalapb',
            },
            {
              label: 'Gitter',
              href: 'https://gitter.im/ScalaPB/community',
            },
          ],
        },
        {
          title: 'More',
          items: [
            /*
            {
              label: 'Blog',
              to: 'blog',
            },
            */
            {
              label: 'GitHub',
              href: 'https://github.com/scalapb/zio-grpc',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} <a href="https://www.linkedin.com/in/nadav-samet/">Nadav Samet</a>`,
    },
    prism: {
      additionalLanguages: ['java', 'protobuf', 'scala'],
      theme: require('prism-react-renderer/themes/nightOwlLight'),
      darkTheme: require('prism-react-renderer/themes/dracula')
    }
  },
  presets: [
    [
      '@docusaurus/preset-classic',
      {
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          sidebarCollapsible: false,
          // Please change this to your repo.
          // editUrl: 'https://github.com/scalapb/zio-grpc/edit/master/foo/docs/',
          path: '../zio-grpc-docs/target/mdoc',
          lastVersion: 'current',

          versions: {
              current: {
                  label: '0.6.x'
              },
              '0.5.x': {
                  label: '0.5.x',
                  path: '0.5.x'
              }
          }
        },
        blog: {
          showReadingTime: true,
          // Please change this to your repo.
          editUrl:
            'https://github.com/scalapb/zio-grpc/edit/master/website/blog/',
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
        googleAnalytics: {
          trackingID: 'UA-346180-20'
        },
      },
    ]
  ]
};

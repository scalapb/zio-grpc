import React from 'react';
import clsx from 'clsx';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './styles.module.css';

const features = [
  {
    imageUrl: 'img/start-quickly-and-scale.svg',
    title: <>Start Quickly and Scale</>,
    description: (
      <>
        Build your first gRPC server in minutes and
        scale to production loads.
      </>
    ),
  },
  {
    imageUrl: 'img/functional-and-type-safe.svg',
    title: <>Functional and Type-safe</>,
    description: (
      <>
        Use the power of Functional Programming and the Scala compiler
        to build robust, correct and fully-featured gRPC servers.
      </>
    ),
  },
  {
    title: <>Stream with ZStream</>,
    imageUrl: 'img/stream-with-zstream.svg',
    description: (
      <>
        Use ZIO's feature-rich ZStreams to create server-streaming, client-streaming
        and bi-directionally streaming RPC endpoints.
      </>
    ),
  },
  {
    title: <>Highly Concurrent</>,
    imageUrl: 'img/highly-concurrent.svg',
    description: (
      <>
        Leverage the power of ZIO to build asynchronous clients and servers
        without deadlocks and race conditions.
      </>
    ),
  },
  {
    title: <>Safe Cancellations</>,
    imageUrl: 'img/safe-cancellations.svg',
    description: (
      <>
        Safely cancel an RPC call by interrupting the effect. Resources on the server will never leak!
      </>
    ),
  },
  {
    title: <>Browser Ready</>,
    imageUrl: 'img/browser-ready.svg',
    description: (
      <>
        ZIO gRPC comes with Scala.js support so you can send RPCs to your service from the browser.
      </>
    ),
  },
];

function Feature({imageUrl, title, description}) {
  const imgUrl = useBaseUrl(imageUrl);
  return (
    <div className={clsx('col col--4', styles.feature)}>
      {imgUrl && (
        <div className="text--center">
          <img className={styles.featureImage} src={imgUrl} alt={title} />
        </div>
      )}
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}

function Home() {
  const context = useDocusaurusContext();
  const {siteConfig = {}} = context;
  const img = useBaseUrl('img/zio-grpc-hero.png');
  return (
    <Layout
      title={`ZIO gRPC: Write gRPC services and clients with ZIO`}
      description="ZIO gRPC lets you write purely functional gRPC services in Scala using ZIO.">
      <header className={clsx('hero hero--primary', styles.heroBanner)}>
        <div className="container">
          <img src={img} width="80%"/>
          <p className="hero__subtitle">{siteConfig.tagline}</p>
          <div className={styles.buttons}>
            <Link
              className={clsx(
                // 'button button--outline button--secondary button--lg button-zio',
                styles.indexCtasGetStartedButton,
              )}
              to={useBaseUrl('docs/')}>
              Get Started
            </Link>
          </div>
        </div>
      </header>
      <main>
        {features && features.length > 0 && (
          <section className={styles.features}>
            <div className="container">
              <div className="row">
                {features.map((props, idx) => (
                  <Feature key={idx} {...props} />
                ))}
              </div>
            </div>
          </section>
        )}
      </main>
    </Layout>
  );
}

export default Home;
